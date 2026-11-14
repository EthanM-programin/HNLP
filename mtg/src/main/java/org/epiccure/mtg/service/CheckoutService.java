package org.epiccure.mtg.service;

import org.epiccure.mtg.api.ApiModels.*;
import org.epiccure.mtg.common.ApiException;
import org.epiccure.mtg.model.*;
import org.epiccure.mtg.repository.*;
import org.epiccure.mtg.rules.Flag;
import org.epiccure.mtg.rules.FlagResult;
import org.epiccure.mtg.rules.Ruleset;
import org.epiccure.mtg.rules.RulesEngine;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Epic 4 / use case "Enforce Clinical Checkout Guardrails" (US-4.1, US-4.3).
 *
 * The manifest is what the volunteer scans at the exit (scannedCodes). If the terminal sends no scans,
 * the session's cart is used. Every line is re-checked against the frozen ruleset AND the Red SKU array
 * from activation. Any RED line = HARD_STOP: nothing is decremented until each one is removed (audited).
 * Completion decrements stock FIFO (earliest expiry, then earliest arrival) under row locks, in one transaction.
 */
@Service
public class CheckoutService {

    public static final String OK = "OK", HARD_STOP = "HARD_STOP";

    private final SessionService sessionService;
    private final ShopperService shopper;
    private final CartItemRepository cart;
    private final InventoryLotRepository lots;
    private final CheckoutLogRepository logs;
    private final CheckoutLineRepository lines;
    private final CheckoutOverrideRepository overrides;
    private final ProductRepository products;
    private final ShoppingSessionRepository sessions;
    private final QueueService queue;
    private final StockService stock;
    private final RulesEngine engine;
    private final ObjectMapper json;

    public CheckoutService(SessionService sessionService, ShopperService shopper, CartItemRepository cart,
                           InventoryLotRepository lots, CheckoutLogRepository logs, CheckoutLineRepository lines,
                           CheckoutOverrideRepository overrides, ProductRepository products,
                           ShoppingSessionRepository sessions, QueueService queue, StockService stock,
                           RulesEngine engine, ObjectMapper json) {
        this.sessionService = sessionService;
        this.shopper = shopper;
        this.cart = cart;
        this.lots = lots;
        this.logs = logs;
        this.lines = lines;
        this.overrides = overrides;
        this.products = products;
        this.sessions = sessions;
        this.queue = queue;
        this.stock = stock;
        this.engine = engine;
        this.json = json;
    }

    private record Line(Product product, int quantity, boolean inCart, boolean scanned) {}

    @Transactional(readOnly = true)
    public ValidationView validate(String token, List<String> scannedCodes) {
        ShoppingSession s = sessionService.requireActive(token);
        return validate(s, scannedCodes);
    }

    /** Volunteer removes a RED (or any) item from the manifest. Always audited in checkout_override. */
    @Transactional
    public ValidationView remove(String token, RemoveRequest req, String volunteer, List<String> scannedCodes) {
        ShoppingSession s = sessionService.requireActive(token);
        if (req == null || req.productId() == null) throw ApiException.badRequest("PRODUCT_REQUIRED", "Which item should be removed?");
        Product p = products.findById(req.productId())
                .orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "Unknown product."));
        cart.findBySessionIdAndProductId(s.getId(), p.getId()).ifPresent(cart::delete);
        cart.flush();

        CheckoutOverride o = new CheckoutOverride();
        o.setSession(s);
        o.setProduct(p);
        o.setAction("REMOVED");
        o.setVolunteerUsername(volunteer);
        o.setReason(req.reason());
        overrides.save(o);
        sessionService.touch(s);

        List<String> remaining = scannedCodes == null ? null : new ArrayList<>(scannedCodes);
        if (remaining != null) remaining.removeIf(c -> matches(p, c));
        return validate(s, remaining);
    }

    @Transactional
    public Receipt complete(String token, List<String> scannedCodes, String volunteer) {
        ShoppingSession s = sessionService.requireActive(token);
        ValidationView v = validate(s, scannedCodes);
        if (HARD_STOP.equals(v.status())) {
            throw new ApiException(HttpStatus.CONFLICT, HARD_STOP,
                    v.restricted().size() + " restricted item(s) must be removed before checkout can complete.", v);
        }
        if (v.lines().isEmpty()) throw ApiException.badRequest("EMPTY_MANIFEST", "There are no items to check out.");

        CheckoutLog log = new CheckoutLog();
        log.setSession(s);
        log.setVolunteerUsername(volunteer);
        log.setItemCount(v.totalUnits());
        log.setTotalWeightLb(v.totalWeightLb());
        log.setCompletedAt(Instant.now());
        logs.save(log);

        // Lock products in id order so two simultaneous checkouts can't deadlock each other.
        List<CheckoutLineView> ordered = new ArrayList<>(v.lines());
        ordered.sort(Comparator.comparing(l -> l.item().productId()));
        List<ReceiptLine> receiptLines = new ArrayList<>();
        Set<Long> touchedLots = new HashSet<>();
        for (CheckoutLineView lv : ordered) {
            Product p = products.getReferenceById(lv.item().productId());
            int need = lv.quantity();
            List<LotDraw> draws = new ArrayList<>();
            need = draw(lots.lockFloorLotsForCheckout(p.getId()), need, p, log, draws, touchedLots);
            if (need > 0) need = draw(lots.lockLotsForCheckout(p.getId()), need, p, log, draws, touchedLots);
            receiptLines.add(new ReceiptLine(lv.item().sku(), lv.item().name(), lv.quantity(),
                    p.getWeightLb().multiply(BigDecimal.valueOf(lv.quantity())), draws, need));
        }

        s.setStatus(SessionService.COMPLETED);
        s.setEndedAt(Instant.now());
        sessions.saveAndFlush(s);

        List<ExpiryAlert> alerts = new ArrayList<>();
        LocalDate cutoff = LocalDate.now().plusDays(7);
        for (Long lotId : touchedLots) {
            lots.findById(lotId).ifPresent(l -> {
                if (l.getExpiresAt() != null && !l.getExpiresAt().isAfter(cutoff) && l.getQuantityRemaining() > 0) {
                    alerts.add(alert(l));
                }
            });
        }
        int promoted = queue.promoteWaiting();      // a spot just opened on the floor

        Receipt receipt = new Receipt(log.getId(), s.getSessionToken(), s.getClient().getFirstName(), volunteer,
                log.getCompletedAt(), receiptLines, v.totalUnits(), v.totalWeightLb(), alerts, promoted);
        log.setReceiptJson(json.writeValueAsString(receipt));
        logs.save(log);
        return receipt;
    }

    private int draw(List<InventoryLot> candidates, int need, Product p, CheckoutLog log, List<LotDraw> draws, Set<Long> touched) {
        for (InventoryLot l : candidates) {
            if (need <= 0) break;
            int available = l.getQuantityRemaining();
            if (available <= 0) continue;
            int take = Math.min(available, need);
            l.setQuantityRemaining(available - take);
            need -= take;
            touched.add(l.getId());

            CheckoutLine cl = new CheckoutLine();
            cl.setCheckout(log);
            cl.setProduct(p);
            cl.setLot(l);
            cl.setQuantity(take);
            cl.setWeightLb(p.getWeightLb().multiply(BigDecimal.valueOf(take)));
            cl.setFundingStream(l.getFundingStream());
            lines.save(cl);
            draws.add(new LotDraw(l.getId(), take, l.getFundingStream(), l.getExpiresAt(), StockService.view(l.getLocation()).label()));
        }
        return need;
    }

    private ValidationView validate(ShoppingSession s, List<String> scannedCodes) {
        Ruleset r = sessionService.ruleset(s);
        Set<String> redSkus = sessionService.restrictedSkus(s);
        Map<Long, StockService.FloorStock> floor = stock.floorStock();
        Map<Long, CartItem> inCart = new LinkedHashMap<>();
        for (CartItem c : cart.findWithProduct(s.getId())) inCart.put(c.getProduct().getId(), c);

        List<Line> manifest = new ArrayList<>();
        List<String> mismatches = new ArrayList<>();
        if (scannedCodes == null || scannedCodes.isEmpty()) {
            for (CartItem c : inCart.values()) manifest.add(new Line(c.getProduct(), c.getQuantity(), true, false));
        } else {
            Map<Long, Integer> counts = new LinkedHashMap<>();
            Map<Long, Product> byId = new HashMap<>();
            for (String code : scannedCodes) {
                Product p = shopper.findProduct(code);
                byId.put(p.getId(), p);
                counts.merge(p.getId(), 1, Integer::sum);
            }
            counts.forEach((id, qty) -> {
                CartItem c = inCart.get(id);
                manifest.add(new Line(byId.get(id), qty, c != null, true));
                if (c == null) mismatches.add(byId.get(id).getName() + " was not scanned in the aisle (not in the digital cart)");
                else if (c.getQuantity() != qty) mismatches.add(byId.get(id).getName() + ": " + qty + " at exit vs " + c.getQuantity() + " in cart");
            });
            inCart.forEach((id, c) -> {
                if (!counts.containsKey(id)) mismatches.add(c.getProduct().getName() + " is in the digital cart but was not scanned at the exit");
            });
        }

        List<CheckoutLineView> out = new ArrayList<>();
        List<CheckoutLineView> restricted = new ArrayList<>();
        int units = 0;
        BigDecimal weight = BigDecimal.ZERO;
        for (Line l : manifest) {
            FlagResult f = engine.evaluate(l.product(), r);
            if (f.flag() != Flag.RED && redSkus.contains(l.product().getSku())) {   // belt and braces: activation-time array
                List<String> reasons = new ArrayList<>(f.reasons());
                reasons.add("On this visit's restricted list");
                f = new FlagResult(Flag.RED, f.maxRatio(), reasons);
            }
            CheckoutLineView lv = new CheckoutLineView(StockService.item(l.product(), f, floor.get(l.product().getId())),
                    l.quantity(), l.inCart(), l.scanned());
            out.add(lv);
            if (f.flag() == Flag.RED) restricted.add(lv);
            units += l.quantity();
            weight = weight.add(l.product().getWeightLb().multiply(BigDecimal.valueOf(l.quantity())));
        }
        return new ValidationView(restricted.isEmpty() ? OK : HARD_STOP, out, restricted, mismatches, units, weight);
    }

    private static boolean matches(Product p, String code) {
        String c = code == null ? "" : code.trim();
        return c.equals(p.getBarcode()) || c.equalsIgnoreCase(p.getSku());
    }

    static ExpiryAlert alert(InventoryLot l) {
        return new ExpiryAlert(l.getId(), l.getProduct().getName(), StockService.view(l.getLocation()).label(),
                l.getExpiresAt(), ChronoUnit.DAYS.between(LocalDate.now(), l.getExpiresAt()), l.getQuantityRemaining());
    }
}
