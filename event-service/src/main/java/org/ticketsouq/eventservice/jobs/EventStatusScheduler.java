package org.ticketsouq.eventservice.jobs;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.ticketsouq.eventservice.model.Event;
import org.ticketsouq.eventservice.model.enums.EventStatus;
import org.ticketsouq.eventservice.repository.EventRepository;
import org.ticketsouq.eventservice.service.EventService;
import org.ticketsouq.sharedmodule.EventService.events.EventCancelledEvent;
import org.ticketsouq.sharedmodule.EventService.events.EventCreatedEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventStatusScheduler {

    private final TaskScheduler taskScheduler;
    private final EventRepository eventRepository;
    private final EventService eventService;

    private final ConcurrentHashMap<UUID, ScheduledFuture<?>> activationTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ScheduledFuture<?>> completionTasks = new ConcurrentHashMap<>();

    @PostConstruct
    public void recoverOnStartup() {
        Instant now = Instant.now();
        List<Event> events = eventRepository.findByStatusIn(List.of(EventStatus.PUBLISHED, EventStatus.ACTIVE));
        int scheduledActivations = 0;
        int scheduledCompletions = 0;

        for (Event event : events) {
            if (event.getStatus() == EventStatus.PUBLISHED) {
                if (event.getStartDate().isAfter(now)) {
                    scheduleActivation(event.getId(), event.getStartDate());
                    scheduledActivations++;
                } else if (event.getFinishDate().isAfter(now)) {
                    eventService.activateEvent(event.getId());
                    scheduleCompletion(event.getId(), event.getFinishDate());
                    scheduledCompletions++;
                } else {
                    eventService.completeEventDirectly(event.getId());
                }
            } else {
                if (event.getFinishDate().isAfter(now)) {
                    scheduleCompletion(event.getId(), event.getFinishDate());
                    scheduledCompletions++;
                } else {
                    eventService.completeEvent(event.getId());
                }
            }
        }

        log.info("Scheduled {} future activations and {} future completions", scheduledActivations, scheduledCompletions);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEventCreated(EventCreatedEvent event) {
        scheduleActivation(event.eventId(), event.startDateTime());
        scheduleCompletion(event.eventId(), event.endDateTime());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEventCancelled(EventCancelledEvent event) {
        cancelScheduledTasks(event.eventId());
    }

    public void scheduleActivation(UUID eventId, Instant startDate) {
        removeOldSchedule(eventId,activationTasks);
        ScheduledFuture<?> future = taskScheduler.schedule(() -> eventService.activateEvent(eventId), startDate);
        activationTasks.put(eventId, future);
    }

    public void scheduleCompletion(UUID eventId, Instant finishDate) {
        removeOldSchedule(eventId,completionTasks);
        ScheduledFuture<?> future = taskScheduler.schedule(() -> eventService.completeEvent(eventId), finishDate);
        completionTasks.put(eventId, future);
    }

    public void cancelScheduledTasks(UUID eventId) {
        ScheduledFuture<?> activation = activationTasks.remove(eventId);
        if (activation != null) {
            activation.cancel(false);
        }
        ScheduledFuture<?> completion = completionTasks.remove(eventId);
        if (completion != null) {
            completion.cancel(false);
        }
    }

    private void removeOldSchedule(UUID eventId,ConcurrentHashMap<UUID, ScheduledFuture<?>> tasks){
        ScheduledFuture<?> existing = tasks.get(eventId);
        if (existing != null && !existing.isDone()) {
            existing.cancel(false);
        }
    }
}
