package org.ticketsouq.apigateway.jobs;

import lombok.RequiredArgsConstructor;
import org.ticketsouq.apigateway.repository.AccessTokenRepository;
import org.ticketsouq.apigateway.repository.RefreshTokenRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class CleaningJobs {

    private final RefreshTokenRepository refreshTokenRepository;
    private final AccessTokenRepository accessTokenRepository;

    @Scheduled(cron = "0 0 */2 * * *")
    @Transactional
    public void RedisTokenSessionCleanUpJob() {
        refreshTokenRepository.findDistinctUserId().forEach(accessTokenRepository::removeDeadSessions);
    }

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void RefreshTokenCleanupJob() {
        refreshTokenRepository.deleteByRevokedTrueOrExpiryDateBefore(Instant.now());
    }

}
