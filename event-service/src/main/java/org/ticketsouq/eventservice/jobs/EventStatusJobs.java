package org.ticketsouq.eventservice.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.eventservice.service.EventService;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventStatusJobs {

    private final EventService eventService;

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void activateScheduledEvents() {
        int count = eventService.activateScheduledEvents();
        if (count > 0) {
            log.info("Activated {} scheduled events", count);
        }
    }

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void completeExpiredEvents() {
        int count = eventService.completeExpiredEvents();
        if (count > 0) {
            log.info("Completed {} expired events", count);
        }
    }
}
