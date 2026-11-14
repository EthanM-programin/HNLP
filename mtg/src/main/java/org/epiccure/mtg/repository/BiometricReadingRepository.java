package org.epiccure.mtg.repository;

import org.epiccure.mtg.model.BiometricReading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BiometricReadingRepository extends JpaRepository<BiometricReading, Long> {
    @Query("select b from BiometricReading b join fetch b.client order by b.recordedOn")
    List<BiometricReading> findAllWithClient();
}
