package org.epiccure.mtg.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "shopping_session")
@Getter @Setter @NoArgsConstructor
public class ShoppingSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "session_token", nullable = false, unique = true) private String sessionToken;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "client_id", nullable = false) private Client client;
    /** WAITING, ACTIVE, COMPLETED, EXPIRED */
    @Column(nullable = false) private String status;
    @Column(name = "queue_position") private Integer queuePosition;
    @Column(name = "tier_code") private String tierCode;
    @Column(name = "household_size", nullable = false) private Integer householdSize;
    @Column(name = "ruleset_snapshot", columnDefinition = "text") private String rulesetSnapshot;
    @Column(name = "ruleset_hash") private String rulesetHash;
    @Column(name = "restricted_skus", columnDefinition = "text") private String restrictedSkus;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    @Column(name = "activated_at") private Instant activatedAt;
    @Column(name = "last_activity_at", nullable = false) private Instant lastActivityAt = Instant.now();
    @Column(name = "ended_at") private Instant endedAt;
}
