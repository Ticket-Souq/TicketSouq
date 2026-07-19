package org.ticketsouq.eventservice.jobs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.ticketsouq.eventservice.model.Event;
import org.ticketsouq.eventservice.model.enums.EventStatus;
import org.ticketsouq.eventservice.repository.EventRepository;
import org.ticketsouq.eventservice.service.EventService;
import org.ticketsouq.sharedmodule.EventService.events.EventCancelledEvent;
import org.ticketsouq.sharedmodule.EventService.events.EventCreatedEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventStatusSchedulerTest {

    @Mock private TaskScheduler taskScheduler;
    @Mock private EventRepository eventRepository;
    @Mock private EventService eventService;

    private EventStatusScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new EventStatusScheduler(taskScheduler, eventRepository, eventService);
    }

    private ScheduledFuture mockFuture() {
        return mock(ScheduledFuture.class);
    }

    @Test
    @DisplayName("Should schedule activation and store the future")
    void givenEventIdAndStartDate_whenScheduleActivation_thenScheduleAndStore() {
        UUID eventId = UUID.randomUUID();
        Instant startDate = Instant.now().plusSeconds(3600);
        ScheduledFuture future = mockFuture();
        when(taskScheduler.schedule(any(Runnable.class), eq(startDate))).thenReturn(future);

        scheduler.scheduleActivation(eventId, startDate);

        verify(taskScheduler).schedule(any(Runnable.class), eq(startDate));
    }

    @Test
    @DisplayName("Should cancel existing schedule when scheduling activation for same event")
    void givenExistingSchedule_whenScheduleActivationAgain_thenCancelOld() {
        UUID eventId = UUID.randomUUID();
        Instant startDate = Instant.now().plusSeconds(3600);
        ScheduledFuture firstFuture = mockFuture();
        when(firstFuture.isDone()).thenReturn(false);
        ScheduledFuture secondFuture = mockFuture();
        when(taskScheduler.schedule(any(Runnable.class), eq(startDate)))
            .thenReturn(firstFuture)
            .thenReturn(secondFuture);

        scheduler.scheduleActivation(eventId, startDate);
        scheduler.scheduleActivation(eventId, startDate);

        verify(firstFuture).cancel(false);
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), eq(startDate));
    }

    @Test
    @DisplayName("Should not cancel old schedule if it is already done")
    void givenCompletedSchedule_whenScheduleActivationAgain_thenNotCancelOld() {
        UUID eventId = UUID.randomUUID();
        Instant startDate = Instant.now().plusSeconds(3600);
        ScheduledFuture firstFuture = mockFuture();
        when(firstFuture.isDone()).thenReturn(true);
        ScheduledFuture secondFuture = mockFuture();
        when(taskScheduler.schedule(any(Runnable.class), eq(startDate)))
            .thenReturn(firstFuture)
            .thenReturn(secondFuture);

        scheduler.scheduleActivation(eventId, startDate);
        scheduler.scheduleActivation(eventId, startDate);

        verify(firstFuture, never()).cancel(false);
    }

    @Test
    @DisplayName("Should schedule completion and store the future")
    void givenEventIdAndFinishDate_whenScheduleCompletion_thenScheduleAndStore() {
        UUID eventId = UUID.randomUUID();
        Instant finishDate = Instant.now().plusSeconds(7200);
        ScheduledFuture future = mockFuture();
        when(taskScheduler.schedule(any(Runnable.class), eq(finishDate))).thenReturn(future);

        scheduler.scheduleCompletion(eventId, finishDate);

        verify(taskScheduler).schedule(any(Runnable.class), eq(finishDate));
    }

    @Test
    @DisplayName("Should cancel both activation and completion tasks")
    void givenScheduledTasks_whenCancelScheduledTasks_thenCancelBoth() {
        UUID eventId = UUID.randomUUID();
        Instant startDate = Instant.now().plusSeconds(3600);
        Instant finishDate = Instant.now().plusSeconds(7200);
        ScheduledFuture activationFuture = mockFuture();
        ScheduledFuture completionFuture = mockFuture();
        when(taskScheduler.schedule(any(Runnable.class), eq(startDate))).thenReturn(activationFuture);
        when(taskScheduler.schedule(any(Runnable.class), eq(finishDate))).thenReturn(completionFuture);

        scheduler.scheduleActivation(eventId, startDate);
        scheduler.scheduleCompletion(eventId, finishDate);
        scheduler.cancelScheduledTasks(eventId);

        verify(activationFuture).cancel(false);
        verify(completionFuture).cancel(false);
    }

    @Test
    @DisplayName("Should do nothing when cancelling tasks for an event with no schedules")
    void givenNoScheduledTasks_whenCancelScheduledTasks_thenDoNothing() {
        scheduler.cancelScheduledTasks(UUID.randomUUID());
    }

    @Test
    @DisplayName("Should schedule activation and completion on event created")
    void givenEventCreatedEvent_whenOnEventCreated_thenScheduleActivationAndCompletion() {
        UUID eventId = UUID.randomUUID();
        Instant startDate = Instant.now().plusSeconds(3600);
        Instant finishDate = Instant.now().plusSeconds(7200);
        EventCreatedEvent event = new EventCreatedEvent(eventId, "title", "org", UUID.randomUUID(), "ZONE", startDate, finishDate);
        ScheduledFuture activationFuture = mockFuture();
        ScheduledFuture completionFuture = mockFuture();
        when(taskScheduler.schedule(any(Runnable.class), eq(startDate))).thenReturn(activationFuture);
        when(taskScheduler.schedule(any(Runnable.class), eq(finishDate))).thenReturn(completionFuture);

        scheduler.onEventCreated(event);

        verify(taskScheduler).schedule(any(Runnable.class), eq(startDate));
        verify(taskScheduler).schedule(any(Runnable.class), eq(finishDate));
    }

    @Test
    @DisplayName("Should cancel scheduled tasks on event cancelled")
    void givenEventCancelledEvent_whenOnEventCancelled_thenCancelScheduledTasks() {
        UUID eventId = UUID.randomUUID();
        EventCancelledEvent event = new EventCancelledEvent(UUID.randomUUID(), eventId, UUID.randomUUID(), Instant.now());
        Instant startDate = Instant.now().plusSeconds(3600);
        ScheduledFuture future = mockFuture();
        when(taskScheduler.schedule(any(Runnable.class), eq(startDate))).thenReturn(future);

        scheduler.scheduleActivation(eventId, startDate);
        scheduler.onEventCancelled(event);

        verify(future).cancel(false);
    }

    @Test
    @DisplayName("Should schedule activation for PUBLISHED events with future start date")
    void givenPublishedEventsWithFutureStart_whenRecoverOnStartup_thenScheduleActivation() {
        UUID eventId = UUID.randomUUID();
        Event event = Event.builder()
            .id(eventId)
            .status(EventStatus.PUBLISHED)
            .startDate(Instant.now().plusSeconds(86400))
            .finishDate(Instant.now().plusSeconds(86400 * 2))
            .build();
        when(eventRepository.findByStatusIn(List.of(EventStatus.PUBLISHED, EventStatus.ACTIVE)))
            .thenReturn(List.of(event));
        ScheduledFuture future = mockFuture();
        when(taskScheduler.schedule(any(Runnable.class), eq(event.getStartDate()))).thenReturn(future);

        scheduler.recoverOnStartup();

        verify(taskScheduler).schedule(any(Runnable.class), eq(event.getStartDate()));
    }

    @Test
    @DisplayName("Should activate and schedule completion for PUBLISHED events with past start and future finish")
    void givenPublishedEventsWithPastStartFutureFinish_whenRecoverOnStartup_thenActivateAndScheduleCompletion() {
        UUID eventId = UUID.randomUUID();
        Event event = Event.builder()
            .id(eventId)
            .status(EventStatus.PUBLISHED)
            .startDate(Instant.now().minusSeconds(3600))
            .finishDate(Instant.now().plusSeconds(86400))
            .build();
        when(eventRepository.findByStatusIn(List.of(EventStatus.PUBLISHED, EventStatus.ACTIVE)))
            .thenReturn(List.of(event));
        ScheduledFuture future = mockFuture();
        when(taskScheduler.schedule(any(Runnable.class), eq(event.getFinishDate()))).thenReturn(future);

        scheduler.recoverOnStartup();

        verify(eventService).activateEvent(eventId);
        verify(taskScheduler).schedule(any(Runnable.class), eq(event.getFinishDate()));
    }

    @Test
    @DisplayName("Should complete directly for PUBLISHED events with past start and past finish")
    void givenPublishedEventsWithPastStartPastFinish_whenRecoverOnStartup_thenCompleteDirectly() {
        UUID eventId = UUID.randomUUID();
        Event event = Event.builder()
            .id(eventId)
            .status(EventStatus.PUBLISHED)
            .startDate(Instant.now().minusSeconds(86400 * 2))
            .finishDate(Instant.now().minusSeconds(86400))
            .build();
        when(eventRepository.findByStatusIn(List.of(EventStatus.PUBLISHED, EventStatus.ACTIVE)))
            .thenReturn(List.of(event));

        scheduler.recoverOnStartup();

        verify(eventService).completeEventDirectly(eventId);
    }

    @Test
    @DisplayName("Should schedule completion for ACTIVE events with future finish date")
    void givenActiveEventsWithFutureFinish_whenRecoverOnStartup_thenScheduleCompletion() {
        UUID eventId = UUID.randomUUID();
        Event event = Event.builder()
            .id(eventId)
            .status(EventStatus.ACTIVE)
            .startDate(Instant.now().minusSeconds(3600))
            .finishDate(Instant.now().plusSeconds(86400))
            .build();
        when(eventRepository.findByStatusIn(List.of(EventStatus.PUBLISHED, EventStatus.ACTIVE)))
            .thenReturn(List.of(event));
        ScheduledFuture future = mockFuture();
        when(taskScheduler.schedule(any(Runnable.class), eq(event.getFinishDate()))).thenReturn(future);

        scheduler.recoverOnStartup();

        verify(taskScheduler).schedule(any(Runnable.class), eq(event.getFinishDate()));
    }

    @Test
    @DisplayName("Should complete for ACTIVE events with past finish date")
    void givenActiveEventsWithPastFinish_whenRecoverOnStartup_thenComplete() {
        UUID eventId = UUID.randomUUID();
        Event event = Event.builder()
            .id(eventId)
            .status(EventStatus.ACTIVE)
            .startDate(Instant.now().minusSeconds(86400 * 2))
            .finishDate(Instant.now().minusSeconds(86400))
            .build();
        when(eventRepository.findByStatusIn(List.of(EventStatus.PUBLISHED, EventStatus.ACTIVE)))
            .thenReturn(List.of(event));

        scheduler.recoverOnStartup();

        verify(eventService).completeEvent(eventId);
    }

    @Test
    @DisplayName("Should handle empty event list on startup")
    void givenNoEvents_whenRecoverOnStartup_thenDoNothing() {
        when(eventRepository.findByStatusIn(List.of(EventStatus.PUBLISHED, EventStatus.ACTIVE)))
            .thenReturn(List.of());

        scheduler.recoverOnStartup();

        verify(eventService, never()).activateEvent(any());
        verify(eventService, never()).completeEvent(any());
        verify(eventService, never()).completeEventDirectly(any());
    }
}
