package org.epiccure.mtg.repository;

import org.epiccure.mtg.model.CheckoutLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CheckoutLogRepository extends JpaRepository<CheckoutLog, Long> {
    Optional<CheckoutLog> findBySessionId(Long sessionId);
}
