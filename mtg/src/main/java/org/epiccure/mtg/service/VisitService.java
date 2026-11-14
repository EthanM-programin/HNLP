package org.epiccure.mtg.service;

import org.epiccure.mtg.common.ApiException;
import org.epiccure.mtg.model.ShoppingSession;
import org.epiccure.mtg.repository.CartItemRepository;
import org.epiccure.mtg.repository.ShoppingSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Ending a visit without a checkout:
 *  - leave(): the shopper chose to go (or leave the line). Cart is emptied, status COMPLETED, no checkout_log, so
 *    nothing is decremented and analytics only count real checkouts.
 *  - away(): the tab was closed. After app.session.leave-grace-seconds with no request from that page, the visit
 *    ends as EXPIRED. The freed spot goes to the next person in line either way.
 */
@Service
public class VisitService {

    private static final Logger log = LoggerFactory.getLogger(VisitService.class);

    public record LeaveResult(String status, int itemsReturned, int promotedFromQueue) {}

    private final ShoppingSessionRepository sessions;
    private final CartItemRepository cart;
    private final SessionService sessionService;
    private final QueueService queue;
    private final Duration grace;

    public VisitService(ShoppingSessionRepository sessions, CartItemRepository cart, SessionService sessionService,
                        QueueService queue, @Value("${app.session.leave-grace-seconds:15}") long graceSeconds) {
        this.sessions = sessions;
        this.cart = cart;
        this.sessionService = sessionService;
        this.queue = queue;
        this.grace = Duration.ofSeconds(graceSeconds);
    }

    @Transactional
    public LeaveResult leave(String token) {
        ShoppingSession s = sessionService.require(token);
        if (!SessionService.ACTIVE.equals(s.getStatus()) && !SessionService.WAITING.equals(s.getStatus())) {
            throw ApiException.conflict("SESSION_NOT_ACTIVE", "This visit has already ended.");
        }
        int returned = end(s, SessionService.COMPLETED);
        return new LeaveResult(s.getStatus(), returned, queue.promoteWaiting());
    }

    public void away(String token) { sessionService.markLeaving(token, grace); }

    /** Every 5 s: end visits whose tab closed and never came back. */
    @Scheduled(fixedDelay = 5_000, initialDelay = 10_000)
    @Transactional
    public void sweepClosedTabs() {
        List<String> due = sessionService.dueLeaves();
        int ended = 0;
        for (String token : due) {
            ShoppingSession s = sessions.findBySessionToken(token).orElse(null);
            if (s == null) continue;
            if (SessionService.ACTIVE.equals(s.getStatus()) || SessionService.WAITING.equals(s.getStatus())) {
                end(s, SessionService.EXPIRED);
                ended++;
            }
        }
        if (ended > 0) {
            log.info("Ended {} visit(s) whose shopper closed the page", ended);
            queue.promoteWaiting();
        }
    }

    private int end(ShoppingSession s, String status) {
        int returned = cart.deleteBySession(s.getId());
        s.setStatus(status);
        s.setQueuePosition(null);
        s.setEndedAt(Instant.now());
        sessions.saveAndFlush(s);
        return returned;
    }
}
