package org.epiccure.mtg.service;

import org.epiccure.mtg.api.ApiModels.SessionView;
import org.epiccure.mtg.common.ApiException;
import org.epiccure.mtg.model.Client;
import org.epiccure.mtg.model.DietaryTier;
import org.epiccure.mtg.model.ShoppingSession;
import org.epiccure.mtg.repository.ClientRepository;
import org.epiccure.mtg.repository.ShoppingSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Epic 1 / use case "Initialize Shopping Session" (US-1.1, US-1.2, US-1.3). */
@Service
public class CheckInService {

    private final ClientRepository clients;
    private final ShoppingSessionRepository sessions;
    private final SessionService sessionService;
    private final QueueService queue;

    public CheckInService(ClientRepository clients, ShoppingSessionRepository sessions,
                          SessionService sessionService, QueueService queue) {
        this.clients = clients;
        this.sessions = sessions;
        this.sessionService = sessionService;
        this.queue = queue;
    }

    @Transactional
    public SessionView checkIn(String clientCode) {
        if (clientCode == null || clientCode.isBlank()) throw ApiException.badRequest("CLIENT_CODE_REQUIRED", "Please enter your client code.");
        int max = queue.lockAndGetCapacity();          // serialises concurrent check-ins
        queue.expireStale();

        Client c = clients.findByClientCode(clientCode.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.notFound("CLIENT_NOT_FOUND",
                        "We couldn't find that client code. Please see a volunteer."));

        boolean expired = c.getEnrollmentExpiresOn() != null && c.getEnrollmentExpiresOn().isBefore(LocalDate.now());
        if (!"ACTIVE".equals(c.getEnrollmentStatus()) || expired) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ENROLLMENT_INACTIVE",
                    "Your Medically Tailored Groceries enrollment is not active. A volunteer can help you renew it.");
        }

        // Re-scanning at the kiosk returns the visit already in progress instead of creating a duplicate.
        var open = sessions.findFirstByClientIdAndStatusInOrderByCreatedAtDesc(c.getId(),
                List.of(SessionService.WAITING, SessionService.ACTIVE));
        if (open.isPresent()) return view(open.get());

        DietaryTier tier = sessionService.tierFor(c);
        ShoppingSession s = new ShoppingSession();
        s.setSessionToken(UUID.randomUUID().toString().replace("-", ""));
        s.setClient(c);
        s.setHouseholdSize(c.getHouseholdSize());
        s.setTierCode(tier == null ? null : tier.getCode());

        if (queue.activeCount() >= max) {
            s.setStatus(SessionService.WAITING);
            s.setQueuePosition(sessions.maxQueuePosition() + 1);
        } else {
            sessionService.activate(s);
        }
        sessions.saveAndFlush(s);
        return view(s);
    }

    /** Volunteer exit terminal: find the shopper's ACTIVE visit by client code (no new session is created). */
    @Transactional(readOnly = true)
    public SessionView activeByClientCode(String clientCode) {
        if (clientCode == null || clientCode.isBlank()) throw ApiException.badRequest("CLIENT_CODE_REQUIRED", "Enter the shopper's client code.");
        Client c = clients.findByClientCode(clientCode.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.notFound("CLIENT_NOT_FOUND", "No client with that code."));
        ShoppingSession s = sessions.findFirstByClientIdAndStatusInOrderByCreatedAtDesc(c.getId(), List.of(SessionService.ACTIVE))
                .orElseThrow(() -> ApiException.notFound("NO_ACTIVE_SESSION", c.getFirstName() + " doesn't have an active shopping visit right now."));
        return view(s);
    }

    /** Kiosk/phone polling: also tells a WAITING client when they've been let in. */
    @Transactional(readOnly = true)
    public SessionView status(String token) {
        return view(sessionService.require(token));
    }

    private SessionView view(ShoppingSession s) {
        Client c = s.getClient();
        DietaryTier tier = sessionService.tierFor(c);
        Integer place = null;
        if (SessionService.WAITING.equals(s.getStatus()) && s.getQueuePosition() != null) {
            place = (int) sessions.countByStatusAndQueuePositionLessThan(SessionService.WAITING, s.getQueuePosition()) + 1;
        }
        var ruleset = SessionService.ACTIVE.equals(s.getStatus()) ? sessionService.ruleset(s) : null;
        return new SessionView(s.getSessionToken(), s.getStatus(), c.getFirstName(),
                tier == null ? null : tier.getCode(), tier == null ? null : tier.getName(),
                s.getHouseholdSize(), place, SessionService.targets(tier, s.getHouseholdSize()),
                ruleset == null ? List.of() : ruleset.dietFocus(),
                queue.activeCount(), queue.maxCapacity());
    }
}
