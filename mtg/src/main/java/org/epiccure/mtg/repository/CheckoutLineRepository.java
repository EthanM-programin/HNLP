package org.epiccure.mtg.repository;

import org.epiccure.mtg.model.CheckoutLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CheckoutLineRepository extends JpaRepository<CheckoutLine, Long> {
    @Query("select l from CheckoutLine l join fetch l.product")
    List<CheckoutLine> findAllWithProduct();
}
