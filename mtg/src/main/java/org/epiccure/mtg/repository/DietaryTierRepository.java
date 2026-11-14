package org.epiccure.mtg.repository;

import org.epiccure.mtg.model.DietaryTier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DietaryTierRepository extends JpaRepository<DietaryTier, Long> {
    Optional<DietaryTier> findByCode(String code);
}
