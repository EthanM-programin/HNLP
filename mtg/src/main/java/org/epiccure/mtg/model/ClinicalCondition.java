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
@Table(name = "clinical_condition")
@Getter @Setter @NoArgsConstructor
public class ClinicalCondition {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "icd10_prefix", nullable = false, unique = true) private String icd10Prefix;
    @Column(nullable = false) private String name;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "dietary_tier_id") private DietaryTier dietaryTier;
    @Column(name = "max_sodium_mg") private Integer maxSodiumMg;
    @Column(name = "max_sugar_g") private BigDecimal maxSugarG;
    @Column(name = "max_potassium_mg") private Integer maxPotassiumMg;
    @Column(name = "max_phosphorus_mg") private Integer maxPhosphorusMg;
    @Column(name = "max_sat_fat_g") private BigDecimal maxSatFatG;
    @Column(name = "max_carbs_g") private BigDecimal maxCarbsG;
    @Column(name = "source_note") private String sourceNote;
}
