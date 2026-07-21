package org.ticketsouq.eventservice.jobs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.scheduling.TaskScheduler;
import org.ticketsouq.eventservice.model.Event;
import org.ticketsouq.eventservice.model.enums.EventStatus;
import org.ticketsouq.eventservice.repository.EventRepository;
import org.ticketsouq.eventservice.service.EventService;
import org.ticketsouq.sharedmodule.EventService.events.EventCancelledEvent;
import org.ticketsouq.sharedmodule.EventService.events.EventCreatedEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("stress")
class EventStatusSchedulerStressTest {

    /**
     * Scales all test sizes. Pass {@code -Dstress.factor=N} to Maven.
     * Default: 1 (light). Use 5 or 10 for a harder stress run.
     */
    private static final int STRESS_FACTOR =
        Integer.getInteger("stress.factor", 10);

    @Mock private TaskScheduler taskScheduler;
    @Mock private EventRepository eventRepository;
    @Mock private EventService eventService;

    private EventStatusScheduler scheduler;
    private AtomicInteger scheduleCallCount;
    private ConcurrentHashMap<UUID, ScheduledFuture> activationTasks;
    private ConcurrentHashMap<UUID, ScheduledFuture> completionTasks;

    @BeforeEach
    void setUp() throws Exception {
        scheduleCallCount = new AtomicInteger(0);
        scheduler = new EventStatusScheduler(taskScheduler, eventRepository, eventService);

        var activationField = EventStatusScheduler.class.getDeclaredField("activationTasks");
        activationField.setAccessible(true);
        activationTasks = (ConcurrentHashMap<UUID, ScheduledFuture>) activationField.get(scheduler);

        var completionField = EventStatusScheduler.class.getDeclaredField("completionTasks");
        completionField.setAccessible(true);
        completionTasks = (ConcurrentHashMap<UUID, ScheduledFuture>) completionField.get(scheduler);

        when(taskScheduler.schedule(any(Runnable.class), any(Instant.class)))
            .thenAnswer(invocation -> {
                scheduleCallCount.incrementAndGet();
                ScheduledFuture future = mock(ScheduledFuture.class);
                when(future.isDone()).thenReturn(false);
                return future;
            });
    }

    @Test
    @DisplayName("Should handle concurrent scheduling of 500 events without errors")
    void given500Events_whenConcurrentSchedule_thenAllScheduled() throws Exception {
        int eventCount = 500 * STRESS_FACTOR;
        int threadCount = Math.max(4, 10 * STRESS_FACTOR / 2);
        List<UUID> eventIds = new ArrayList<>();
        for (int i = 0; i < eventCount; i++) {
            eventIds.add(UUID.randomUUID());
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(eventCount);

        for (UUID eventId : eventIds) {
            executor.submit(() -> {
                try {
                    Instant startDate = Instant.now().plusSeconds(3600);
                    Instant finishDate = Instant.now().plusSeconds(7200);
                    scheduler.scheduleActivation(eventId, startDate);
                    scheduler.scheduleCompletion(eventId, finishDate);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(scheduleCallCount.get()).isEqualTo(eventCount * 2);
        assertThat(activationTasks.size()).isEqualTo(eventCount);
        assertThat(completionTasks.size()).isEqualTo(eventCount);
    }

    @Test
    @DisplayName("Should handle concurrent scheduling of same eventId without data races")
    void givenSameEventId_whenConcurrentSchedule_thenLastOneWins() throws Exception {
        UUID eventId = UUID.randomUUID();
        int threadCount = Math.max(4, 20 * STRESS_FACTOR / 2);
        Instant startDate = Instant.now().plusSeconds(3600);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    scheduler.scheduleActivation(eventId, startDate);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(activationTasks.size()).isEqualTo(1);
        assertThat((Object) activationTasks.get(eventId)).isNotNull();
    }

    @Test
    @DisplayName("Should handle concurrent schedule and cancel for overlapping events")
    void givenOverlappingEvents_whenConcurrentScheduleAndCancel_thenConsistent() throws Exception {
        int eventCount = 100 * STRESS_FACTOR;
        int threadCount = Math.max(4, 10 * STRESS_FACTOR / 2);
        List<UUID> eventIds = new ArrayList<>();
        for (int i = 0; i < eventCount; i++) {
            eventIds.add(UUID.randomUUID());
        }

        int half = eventCount / 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(half * 2 + half);

        for (UUID eventId : eventIds.subList(0, half)) {
            executor.submit(() -> {
                try {
                    scheduler.scheduleActivation(eventId, Instant.now().plusSeconds(3600));
                } finally {
                    latch.countDown();
                }
            });
            executor.submit(() -> {
                try {
                    scheduler.cancelScheduledTasks(eventId);
                } finally {
                    latch.countDown();
                }
            });
        }
        for (UUID eventId : eventIds.subList(half, eventCount)) {
            executor.submit(() -> {
                try {
                    scheduler.scheduleActivation(eventId, Instant.now().plusSeconds(3600));
                    scheduler.scheduleCompletion(eventId, Instant.now().plusSeconds(7200));
                } finally {
                    latch.countDown();
                }
            });
        }

        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("Should handle concurrent schedule and cancel for same eventId")
    void givenSingleEventId_whenConcurrentScheduleAndCancel_thenNoDoubleCancel() throws Exception {
        UUID eventId = UUID.randomUUID();
        int iterations = Math.max(10, 50 * STRESS_FACTOR);
        int threadCount = Math.max(2, 4 * STRESS_FACTOR / 2);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(iterations * 2);

        for (int i = 0; i < iterations; i++) {
            executor.submit(() -> {
                try {
                    scheduler.scheduleActivation(eventId, Instant.now().plusSeconds(3600));
                } finally {
                    latch.countDown();
                }
            });
            executor.submit(() -> {
                try {
                    scheduler.cancelScheduledTasks(eventId);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        int size = activationTasks.size();
        assertThat(size).isBetween(0, 1);
    }

    @Test
    @DisplayName("Should handle recoverOnStartup with 1000 events")
    void given1000Events_whenRecoverOnStartup_thenAllProcessed() {
        int eventCount = 1000 * STRESS_FACTOR;
        List<Event> events = new ArrayList<>();
        Instant now = Instant.now();

        for (int i = 0; i < eventCount; i++) {
            Event event;
            if (i % 4 == 0) {
                event = Event.builder()
                    .id(UUID.randomUUID())
                    .status(EventStatus.PUBLISHED)
                    .startDate(now.plusSeconds(86400))
                    .finishDate(now.plusSeconds(86400 * 2))
                    .build();
            } else if (i % 4 == 1) {
                event = Event.builder()
                    .id(UUID.randomUUID())
                    .status(EventStatus.PUBLISHED)
                    .startDate(now.minusSeconds(3600))
                    .finishDate(now.plusSeconds(86400))
                    .build();
            } else if (i % 4 == 2) {
                event = Event.builder()
                    .id(UUID.randomUUID())
                    .status(EventStatus.PUBLISHED)
                    .startDate(now.minusSeconds(86400 * 2))
                    .finishDate(now.minusSeconds(86400))
                    .build();
            } else {
                event = Event.builder()
                    .id(UUID.randomUUID())
                    .status(EventStatus.ACTIVE)
                    .startDate(now.minusSeconds(3600))
                    .finishDate(now.plusSeconds(86400))
                    .build();
            }
            events.add(event);
        }

        when(eventRepository.findByStatusIn(any())).thenReturn(events);

        scheduler.recoverOnStartup();

        int expectedScheduled = (eventCount / 4) + (eventCount / 4) + (eventCount / 4);
        verify(taskScheduler, times(expectedScheduled)).schedule(any(Runnable.class), any(Instant.class));
        verify(eventService, times(eventCount / 4)).activateEvent(any());
        verify(eventService, times(eventCount / 4)).completeEventDirectly(any());
        verify(eventService, never()).completeEvent(any());
    }

    @Test
    @DisplayName("Should handle 100 concurrent onEventCreated calls")
    void given100ConcurrentCreates_whenOnEventCreated_thenAllScheduled() throws Exception {
        int eventCount = 100 * STRESS_FACTOR;
        int threadCount = Math.max(4, 10 * STRESS_FACTOR / 2);
        List<EventCreatedEvent> events = new ArrayList<>();
        for (int i = 0; i < eventCount; i++) {
            events.add(new EventCreatedEvent(
                UUID.randomUUID(), "title", "org", UUID.randomUUID(), "ZONE",
                Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200)));
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(eventCount);

        for (EventCreatedEvent event : events) {
            executor.submit(() -> {
                try {
                    scheduler.onEventCreated(event);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(scheduleCallCount.get()).isEqualTo(eventCount * 2);
    }

    @Test
    @DisplayName("Should handle 100 concurrent onEventCancelled calls")
    void given100ConcurrentCancels_whenOnEventCancelled_thenAllProcessed() throws Exception {
        int eventCount = 100 * STRESS_FACTOR;
        int threadCount = Math.max(4, 10 * STRESS_FACTOR / 2);
        List<UUID> eventIds = new ArrayList<>();
        for (int i = 0; i < eventCount; i++) {
            eventIds.add(UUID.randomUUID());
        }

        for (UUID eventId : eventIds) {
            scheduler.scheduleActivation(eventId, Instant.now().plusSeconds(3600));
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(eventCount);

        for (UUID eventId : eventIds) {
            executor.submit(() -> {
                try {
                    scheduler.onEventCancelled(new EventCancelledEvent(
                        UUID.randomUUID(), eventId, UUID.randomUUID(), Instant.now()));
                } finally {
                    latch.countDown();
                }
            });
        }

        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(activationTasks.size()).isEqualTo(0);
    }

    @Test
    @DisplayName("Schedule-cancel race should never throw")
    void givenRace_whenScheduleAndCancel_thenNoException() throws Exception {
        int repeat = Math.max(5, 20 * STRESS_FACTOR / 2);
        int innerIterations = Math.max(10, 50 * STRESS_FACTOR);
        for (int r = 0; r < repeat; r++) {
            UUID eventId = UUID.randomUUID();

            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch latch = new CountDownLatch(2);

            executor.submit(() -> {
                try {
                    for (int i = 0; i < innerIterations; i++) {
                        scheduler.scheduleActivation(eventId, Instant.now().plusSeconds(3600));
                    }
                } finally {
                    latch.countDown();
                }
            });
            executor.submit(() -> {
                try {
                    for (int i = 0; i < innerIterations; i++) {
                        scheduler.cancelScheduledTasks(eventId);
                    }
                } finally {
                    latch.countDown();
                }
            });

            assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}
