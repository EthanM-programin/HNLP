package org.epiccure.mtg.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "checkout_override")
@Getter @Setter @NoArgsConstructor
public class CheckoutOverride {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "session_id", nullable = false) private ShoppingSession session;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id", nullable = false) private Product product;
    /** REMOVED or OVERRIDDEN */
    @Column(nullable = false) private String action;
    @Column(name = "volunteer_username", nullable = false) private String volunteerUsername;
    @Column(columnDefinition = "text") private String reason;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
}
