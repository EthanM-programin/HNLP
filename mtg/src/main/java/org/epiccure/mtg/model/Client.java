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
@Table(name = "client")
@Getter @Setter @NoArgsConstructor
public class Client {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_code", nullable = false, unique = true) private String clientCode;
    @Column(name = "first_name", nullable = false) private String firstName;
    @Column(name = "last_name", nullable = false) private String lastName;
    private String phone;
    @Column(name = "household_size", nullable = false) private Integer householdSize;
    @Column(name = "enrollment_status", nullable = false) private String enrollmentStatus;
    @Column(name = "enrollment_expires_on") private LocalDate enrollmentExpiresOn;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
}
