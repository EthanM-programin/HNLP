package org.epiccure.mtg.repository;

import org.epiccure.mtg.model.ClientMedicalProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClientMedicalProfileRepository extends JpaRepository<ClientMedicalProfile, Long> {
    Optional<ClientMedicalProfile> findByClientId(Long clientId);
    boolean existsByClientId(Long clientId);
}
