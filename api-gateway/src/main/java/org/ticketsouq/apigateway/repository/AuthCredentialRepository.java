package org.ticketsouq.apigateway.repository;

import org.ticketsouq.apigateway.model.AuthCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthCredentialRepository extends JpaRepository<AuthCredential, UUID> {

    Optional<AuthCredential> findByUserId(UUID userId);

    Optional<AuthCredential> findByEmail(String email);

}
