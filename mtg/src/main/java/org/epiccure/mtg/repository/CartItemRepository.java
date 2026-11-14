package org.epiccure.mtg.repository;

import org.epiccure.mtg.model.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    @Query("select c from CartItem c join fetch c.product where c.session.id = :sessionId order by c.addedAt")
    List<CartItem> findWithProduct(@Param("sessionId") Long sessionId);

    Optional<CartItem> findBySessionIdAndProductId(Long sessionId, Long productId);

    @Modifying
    @Query("delete from CartItem c where c.session.id = :sessionId")
    int deleteBySession(@Param("sessionId") Long sessionId);
}
