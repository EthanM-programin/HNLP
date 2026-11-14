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
@Table(name = "client_medical_profile")
@Getter @Setter @NoArgsConstructor
public class ClientMedicalProfile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "client_id", nullable = false, unique = true) private Client client;
    @Convert(converter = EncryptedStringConverter.class) @Column(name = "icd10_codes_enc", columnDefinition = "text") private String icd10Codes;
    @Convert(converter = EncryptedStringConverter.class) @Column(name = "allergies_enc", columnDefinition = "text") private String allergies;
    @Convert(converter = EncryptedStringConverter.class) @Column(name = "notes_enc", columnDefinition = "text") private String notes;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "dietary_tier_id") private DietaryTier dietaryTier;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();
}
