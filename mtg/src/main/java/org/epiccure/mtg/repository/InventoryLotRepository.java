package org.epiccure.mtg.repository;

import jakarta.persistence.LockModeType;
import org.epiccure.mtg.model.InventoryLot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface InventoryLotRepository extends JpaRepository<InventoryLot, Long> {

    /**
     * FIFO checkout: SELECT ... FOR UPDATE, earliest expiry first, then earliest received.
     * Call inside one @Transactional method so concurrent checkouts cannot oversell a lot.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from InventoryLot l where l.product.id = :productId and l.quantityRemaining > 0 "
         + "order by l.expiresAt asc nulls last, l.receivedAt asc")
    List<InventoryLot> lockLotsForCheckout(@Param("productId") Long productId);

    /** Restock/rescue list: lots expiring within the window, ordered by aisle then bin. */
    @Query("select l from InventoryLot l join fetch l.location loc join fetch l.product "
         + "where l.expiresAt <= :cutoff and l.quantityRemaining > 0 "
         + "order by loc.aisle asc, loc.bin asc")
    List<InventoryLot> findExpiringBy(@Param("cutoff") LocalDate cutoff);

    @Query("select coalesce(sum(l.quantityRemaining), 0) from InventoryLot l "
         + "where l.product.id = :productId and l.location.floor = true")
    long floorStock(@Param("productId") Long productId);
}
