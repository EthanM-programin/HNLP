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
@Table(name = "biometric_reading")
@Getter @Setter @NoArgsConstructor
public class BiometricReading {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "client_id", nullable = false) private Client client;
    @Column(nullable = false) private String metric;
    @Column(name = "reading_value", nullable = false) private BigDecimal readingValue;
    @Column(name = "recorded_on", nullable = false) private LocalDate recordedOn;
}
