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
@Table(name = "bin_location")
@Getter @Setter @NoArgsConstructor
public class BinLocation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false) private String zone;
    @Column(nullable = false) private String aisle;
    @Column(nullable = false) private Integer shelf;
    @Column(nullable = false) private Integer bin;
    @Column(name = "is_floor", nullable = false) private Boolean floor;
}
