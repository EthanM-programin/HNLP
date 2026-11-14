package org.epiccure.mtg.repository;

import org.epiccure.mtg.model.ClinicalCondition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicalConditionRepository extends JpaRepository<ClinicalCondition, Long> {
}
