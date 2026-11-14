package org.epiccure.mtg.service;

import org.epiccure.mtg.api.ApiModels.GroceryTargets;
import org.epiccure.mtg.common.ApiException;
import org.epiccure.mtg.model.*;
import org.epiccure.mtg.repository.*;
import org.epiccure.mtg.rules.Flag;
import org.epiccure.mtg.rules.Ruleset;
import org.epiccure.mtg.rules.RulesEngine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session lifecycle + the per-visit ruleset. PHI (ICD-10 codes, allergies) is decrypted only here, at activation,
 * and reduced to numeric ceilings + allergen tags that are frozen on the session. Nothing downstream re-reads PHI.
 */
@Service
@Transactional
public class SessionService {

    public static final String WAITING = "WAITING", ACTIVE = "ACTIVE", COMPLETED = "COMPLETED", EXPIRED = "EXPIRED";

    private final ShoppingSessionRepository sessions;
    private final ClientMedicalProfileRepository profiles;
    private final ClinicalConditionRepository conditions;
    private final DietaryTierRepository tiers;
    private final ProductRepository products;
    private final RulesEngine engine;

    public SessionService(ShoppingSessionRepository sessions, ClientMedicalProfileRepository profiles,
                          ClinicalConditionRepository conditions, DietaryTierRepository tiers,
                          ProductRepository products, RulesEngine engine) {
        this.sessions = sessions;
        this.profiles = profiles;
        this.conditions = conditions;
        this.tiers = tiers;
        this.products = products;
        this.engine = engine;
    }

    /**
     * Tabs that were closed (sendBeacon "away"): token -> when to end the visit. Any later request carrying the
     * token (a page reload, the shopper's other tab, the heartbeat) cancels it, so only a real exit ends the visit.
     */
    private final Map<String, Instant> pendingLeave = new ConcurrentHashMap<>();

    public void markLeaving(String token, Duration grace) { pendingLeave.put(token, Instant.now().plus(grace)); }

    /** Tokens whose grace period ran out (removed from the registry as they're returned). */
    public List<String> dueLeaves() {
        Instant now = Instant.now();
        List<String> due = new ArrayList<>();
        pendingLeave.forEach((t, at) -> { if (at.isBefore(now)) due.add(t); });
        due.forEach(pendingLeave::remove);
        return due;
    }

    public ShoppingSession require(String token) {
        pendingLeave.remove(token);
        return sessions.findBySessionToken(token)
                .orElseThrow(() -> ApiException.notFound("SESSION_NOT_FOUND", "No shopping session for that token."));
    }

    public ShoppingSession requireActive(String token) {
        ShoppingSession s = require(token);
        if (!ACTIVE.equals(s.getStatus())) {
            throw ApiException.conflict("SESSION_NOT_ACTIVE", switch (s.getStatus()) {
                case WAITING -> "You're still in line. We'll let you know when the floor has room.";
                case EXPIRED -> "This session expired after inactivity. Please check in again at the kiosk.";
                default -> "This shopping session has already been checked out.";
            });
        }
        return s;
    }

    /** Heartbeat: every scan/cart action keeps the session alive (self-healing queue). */
    public void touch(ShoppingSession s) { s.setLastActivityAt(Instant.now()); }

    /** WAITING/new -> ACTIVE: compute and freeze the ruleset and the Red SKU array. */
    public void activate(ShoppingSession s) {
        Ruleset r = rulesetFor(s.getClient());
        List<String> red = new ArrayList<>();
        for (Product p : products.findByActiveTrueOrderByIdAsc()) {
            if (engine.evaluate(p, r).flag() == Flag.RED) red.add(p.getSku());
        }
        String snapshot = r.encode();
        s.setRulesetSnapshot(snapshot);
        s.setRulesetHash(sha256(snapshot).substring(0, 16));
        s.setRestrictedSkus(String.join(",", red));
        s.setStatus(ACTIVE);
        s.setQueuePosition(null);
        s.setActivatedAt(Instant.now());
        s.setLastActivityAt(Instant.now());
    }

    @Transactional(readOnly = true)
    public Ruleset rulesetFor(Client c) {
        Optional<ClientMedicalProfile> profile = profiles.findByClientId(c.getId());
        List<String> codes = profile.map(p -> csv(p.getIcd10Codes())).orElse(List.of());
        List<String> allergies = profile.map(p -> csv(p.getAllergies())).orElse(List.of());
        List<ClinicalCondition> matched = engine.matchConditions(conditions.findAll(), codes);
        return engine.build(matched, codes, allergies);
    }

    public Ruleset ruleset(ShoppingSession s) { return Ruleset.decode(s.getRulesetSnapshot()); }

    public Set<String> restrictedSkus(ShoppingSession s) {
        Set<String> out = new HashSet<>();
        if (s.getRestrictedSkus() != null) {
            for (String sku : s.getRestrictedSkus().split(",")) if (!sku.isBlank()) out.add(sku.trim());
        }
        return out;
    }

    @Transactional(readOnly = true)
    public DietaryTier tierFor(Client c) {
        return profiles.findByClientId(c.getId())
                .map(ClientMedicalProfile::getDietaryTier)
                .or(() -> tiers.findByCode("GENERAL_WELLNESS"))
                .orElse(null);
    }

    /** US-1.2 / use case: daily grocery needs = tier coefficients x household size. */
    public static GroceryTargets targets(DietaryTier t, int household) {
        if (t == null) return new GroceryTargets(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        BigDecimal h = BigDecimal.valueOf(household);
        return new GroceryTargets(t.getProduceLbPerPerson().multiply(h), t.getProteinLbPerPerson().multiply(h),
                t.getDairyUnitsPerPerson().multiply(h), t.getGrainLbPerPerson().multiply(h));
    }

    /** Parses "E11,I10" or the JSON-ish ["E11", "I10"] form. */
    static List<String> csv(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        for (String part : s.replaceAll("[\\[\\]\"]", "").split(",")) {
            if (!part.isBlank()) out.add(part.trim());
        }
        return out;
    }

    static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
