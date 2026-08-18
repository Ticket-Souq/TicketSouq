package org.ticketsouq.apigateway.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_token")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "session_id", unique = true, nullable = false)
    private UUID sessionId;

    @Column(name = "revoked")
    private boolean revoked;

    @Column(name = "expiry_date", nullable = false)
    private Instant expiryDate;
}
