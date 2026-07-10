package org.ticketsouq.apigateway.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_credential")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id", unique = true, nullable = false)
    private UUID userId;

    @Column(name = "email", unique = true, nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "is_verified")
    private Boolean isVerified;

    @Column(name = "last_login")
    private Instant lastLogin;

    @Column(name = "failed_attempts")
    private Integer failedAttempts;

    @Column(name = "is_locked")
    private Boolean locked;

    @Column(name = "locked_until")
    private Instant lockedUntil;
}
