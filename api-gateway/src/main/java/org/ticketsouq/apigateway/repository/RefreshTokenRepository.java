package org.ticketsouq.apigateway.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.ticketsouq.apigateway.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    void deleteByUserId(UUID userId);
    void deleteBySessionId(UUID sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findRefreshTokenBySessionId(UUID sessionId);

    @Query("SELECT DISTINCT RT.userId FROM RefreshToken RT")
    List<UUID> findDistinctUserId();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RefreshToken rt WHERE rt.revoked = true OR rt.expiryDate < :now")
    void deleteByRevokedTrueOrExpiryDateBefore(@Param("now") Instant now);
}
