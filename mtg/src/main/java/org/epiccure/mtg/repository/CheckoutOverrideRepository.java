package org.epiccure.mtg.repository;

import org.epiccure.mtg.model.CheckoutOverride;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckoutOverrideRepository extends JpaRepository<CheckoutOverride, Long> {
}
