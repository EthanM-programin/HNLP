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
@Table(name = "checkout_line")
@Getter @Setter @NoArgsConstructor
public class CheckoutLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "checkout_id", nullable = false) private CheckoutLog checkout;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id", nullable = false) private Product product;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "lot_id", nullable = false) private InventoryLot lot;
    @Column(nullable = false) private Integer quantity;
    @Column(name = "weight_lb", nullable = false) private BigDecimal weightLb;
    @Column(name = "funding_stream") private String fundingStream;
}
