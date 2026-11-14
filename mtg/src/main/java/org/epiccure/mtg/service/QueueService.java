package org.epiccure.mtg.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.epiccure.mtg.api.ApiModels.CapacityView;
import org.epiccure.mtg.model.ShoppingSession;
import org.epiccure.mtg.repository.AppSettingRepository;
import org.epiccure.mtg.repository.ShoppingSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Self-healing capacity queue (US-1.3). Active_Floor_Shoppers is never stored: it is counted from ACTIVE sessions
 * with a recent heartbeat, so it can't drift. Check-ins and promotions serialise on a row lock of the
 * max_floor_capacity setting, so two arrivals can't both take the last spot.
 */
@Service
public class QueueService {

    private static final Logger log = LoggerFactory.getLogger(QueueService.class);

    @PersistenceContext
    private EntityManager em;

    private final ShoppingSessionRepository sessions;
    private final AppSettingRepository settings;
    private final SessionService sessionService;

    public QueueService(ShoppingSessionRepository sessions, AppSettingRepository settings, SessionService sessionService) {
        this.sessions = sessions;
        this.settings = settings;
        this.sessionService = sessionService;
    }

    /** Must be called inside a transaction: SELECT ... FOR UPDATE on the capacity setting acts as the mutex. */
    public int lockAndGetCapacity() {
        List<?> rows = em.createNativeQuery(
                "select setting_value from app_setting where setting_key = 'max_floor_capacity' for update").getResultList();
        return rows.isEmpty() ? 10 : Integer.parseInt(String.valueOf(rows.get(0)).trim());
    }

    public int maxCapacity() { return intSetting("max_floor_capacity", 10); }

    public int timeoutMinutes() { return intSetting("session_timeout_minutes", 45); }

    public Instant cutoff() { return Instant.now().minus(Duration.ofMinutes(timeoutMinutes())); }

    public long activeCount() { return sessions.countActive(cutoff()); }

    public int expireStale() { return sessions.expireStale(cutoff(), Instant.now()); }

    /** Expire abandoned carts, then let WAITING clients in (lowest queue position first) while there is room. */
    @Transactional
    public int promoteWaiting() {
        int max = lockAndGetCapacity();
        int expired = expireStale();
        int promoted = 0;
        while (sessions.countActive(cutoff()) < max) {
            Optional<ShoppingSession> next = sessions.findFirstByStatusOrderByQueuePositionAsc(SessionService.WAITING);
            if (next.isEmpty()) break;
            sessionService.activate(next.get());
            sessions.saveAndFlush(next.get());
            promoted++;
        }
        if (expired > 0 || promoted > 0) log.info("Queue tick: expired {} abandoned session(s), promoted {}", expired, promoted);
        return promoted;
    }

    /** Runs every minute so abandoned carts free their spot even if nobody checks out. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    @Transactional
    public void tick() { promoteWaiting(); }

    @Transactional(readOnly = true)
    public CapacityView capacity() {
        long active = activeCount();
        int max = maxCapacity();
        return new CapacityView(active, max, sessions.countByStatus(SessionService.WAITING), active >= max, timeoutMinutes());
    }

    private int intSetting(String key, int fallback) {
        return settings.findById(key).map(s -> {
            try { return Integer.parseInt(s.getValue().trim()); } catch (NumberFormatException e) { return fallback; }
        }).orElse(fallback);
    }
}
