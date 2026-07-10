package org.ticketsouq.apigateway.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.ticketsouq.apigateway.model.AuthCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthCredentialRepository extends JpaRepository<AuthCredential, UUID> {

    Optional<AuthCredential> findByUserId(UUID userId);

    Optional<AuthCredential> findByEmail(String email);

    boolean existsByEmail(String email);

    @Modifying
    @Query("UPDATE AuthCredential AC SET AC.locked = false, AC.lockedUntil = null WHERE AC.lockedUntil < :now")
    void unlockExpiredAccounts(@Param("now") Instant now);
}
