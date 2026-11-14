package org.epiccure.mtg.repository;

import org.epiccure.mtg.model.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {
    Optional<Client> findByClientCode(String clientCode);
}
