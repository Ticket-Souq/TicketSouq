package org.ticketsouq.eventservice.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.eventservice.repository.SeatLockRepository;
import org.ticketsouq.eventservice.repository.ZoneLockRepository;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventStatusJobs {

    private final SeatLockRepository seatLockRepository;
    private final ZoneLockRepository zoneLockRepository;

    @Scheduled(fixedRate = 30_000)
    @Transactional
    public void expireSeatLocks() {
        int count = seatLockRepository.deleteByExpiresAtBefore(LocalDateTime.now(), 500);
        if (count > 0) {
            log.info("Expired {} seat locks", count);
        }
    }

    @Scheduled(fixedRate = 30_000)
    @Transactional
    public void expireZoneLocks() {
        int count = zoneLockRepository.deleteByExpiresAtBefore(LocalDateTime.now(), 500);
        if (count > 0) {
            log.info("Expired {} zone locks", count);
        }
    }
}
