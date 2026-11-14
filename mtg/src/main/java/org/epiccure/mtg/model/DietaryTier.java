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
@Table(name = "dietary_tier")
@Getter @Setter @NoArgsConstructor
public class DietaryTier {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true) private String code;
    @Column(nullable = false) private String name;
    private String description;
    @Column(name = "produce_lb_per_person", nullable = false) private BigDecimal produceLbPerPerson;
    @Column(name = "protein_lb_per_person", nullable = false) private BigDecimal proteinLbPerPerson;
    @Column(name = "dairy_units_per_person", nullable = false) private BigDecimal dairyUnitsPerPerson;
    @Column(name = "grain_lb_per_person", nullable = false) private BigDecimal grainLbPerPerson;
}
