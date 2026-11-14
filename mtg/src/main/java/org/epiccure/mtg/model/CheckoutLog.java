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
@Table(name = "checkout_log")
@Getter @Setter @NoArgsConstructor
public class CheckoutLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "session_id", nullable = false, unique = true) private ShoppingSession session;
    @Column(name = "volunteer_username", nullable = false) private String volunteerUsername;
    @Column(name = "item_count", nullable = false) private Integer itemCount;
    @Column(name = "total_weight_lb", nullable = false) private BigDecimal totalWeightLb;
    @Column(name = "receipt_json", columnDefinition = "text") private String receiptJson;
    @Column(name = "completed_at", nullable = false) private Instant completedAt = Instant.now();
}
