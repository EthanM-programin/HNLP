package org.epiccure.mtg.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "inventory_lot")
@Getter @Setter @NoArgsConstructor
public class InventoryLot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id", nullable = false) private Product product;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "location_id", nullable = false) private BinLocation location;
    @Column(name = "quantity_remaining", nullable = false) private Integer quantityRemaining;
    @Column(name = "received_at", nullable = false) private Instant receivedAt = Instant.now();
    @Column(name = "expires_at") private LocalDate expiresAt;
    @Column(name = "funding_stream") private String fundingStream;
}
