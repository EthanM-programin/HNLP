package org.epiccure.mtg.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.epiccure.mtg.crypto.EncryptedStringConverter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "product")
@Getter @Setter @NoArgsConstructor
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true) private String sku;
    @Column(unique = true) private String barcode;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private String category;
    private String brand;
    @Column(name = "serving_desc") private String servingDesc;
    @Column(name = "serving_g") private BigDecimal servingG;
    private Integer calories;
    @Column(name = "protein_g") private BigDecimal proteinG;
    @Column(name = "carbs_g") private BigDecimal carbsG;
    @Column(name = "sugar_g") private BigDecimal sugarG;
    @Column(name = "fiber_g") private BigDecimal fiberG;
    @Column(name = "sat_fat_g") private BigDecimal satFatG;
    @Column(name = "sodium_mg") private Integer sodiumMg;
    @Column(name = "potassium_mg") private Integer potassiumMg;
    @Column(name = "phosphorus_mg") private Integer phosphorusMg;
    private String allergens;
    @Column(columnDefinition = "text") private String ingredients;
    @Column(name = "weight_lb", nullable = false) private BigDecimal weightLb = BigDecimal.ONE;
    @Column(name = "min_floor_threshold", nullable = false) private Integer minFloorThreshold = 0;
    @Column(nullable = false) private Boolean active = true;
}
