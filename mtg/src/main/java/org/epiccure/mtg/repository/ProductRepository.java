package org.epiccure.mtg.repository;

import org.epiccure.mtg.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByBarcode(String barcode);
    Optional<Product> findBySkuIgnoreCase(String sku);
    List<Product> findByActiveTrueOrderByIdAsc();
}
