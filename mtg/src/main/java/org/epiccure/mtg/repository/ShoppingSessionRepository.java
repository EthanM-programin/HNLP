package org.epiccure.mtg.repository;

import jakarta.persistence.LockModeType;
import org.epiccure.mtg.model.ShoppingSession;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public interface ShoppingSessionRepository extends JpaRepository<ShoppingSession, Long> {

    Optional<ShoppingSession> findBySessionToken(String sessionToken);

    /** The client's open visit (WAITING or ACTIVE), so a second kiosk check-in returns the same session. */
    Optional<ShoppingSession> findFirstByClientIdAndStatusInOrderByCreatedAtDesc(Long clientId, Collection<String> statuses);

    /** Active_Floor_Shoppers: ACTIVE sessions with a heartbeat newer than the cutoff (now - 45 min). */
    @Query("select count(s) from ShoppingSession s where s.status = 'ACTIVE' and s.lastActivityAt > :cutoff")
    long countActive(@Param("cutoff") Instant cutoff);

    /** Marks abandoned carts EXPIRED so the floor never deadlocks. Returns rows changed. */
    @Modifying
    @Query("update ShoppingSession s set s.status = 'EXPIRED', s.endedAt = :now "
         + "where s.status = 'ACTIVE' and s.lastActivityAt <= :cutoff")
    int expireStale(@Param("cutoff") Instant cutoff, @Param("now") Instant now);

    /** Lowest queue_position WAITING session, row-locked so two promoters cannot take the same one. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ShoppingSession> findFirstByStatusOrderByQueuePositionAsc(String status);

    @Query("select coalesce(max(s.queuePosition), 0) from ShoppingSession s where s.status = 'WAITING'")
    int maxQueuePosition();

    long countByStatus(String status);

    long countByStatusAndQueuePositionLessThan(String status, Integer queuePosition);
}
