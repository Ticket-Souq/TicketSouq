package org.ticketsouq.eventservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.ticketsouq.eventservice.dto.*;
import org.ticketsouq.eventservice.model.*;
import org.ticketsouq.eventservice.model.enums.BookingModel;
import org.ticketsouq.eventservice.model.enums.EventStatus;
import org.ticketsouq.eventservice.model.enums.SeatStatus;
import org.ticketsouq.eventservice.repository.*;
import org.ticketsouq.sharedmodule.EventService.exception.ZoneCapacityExceededException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @deprecated Replaced by {@link LockServiceIntegrationTest} which uses real PostgreSQL
 *             via Testcontainers for proper pessimistic-locking concurrency verification.
 */
@Deprecated
@ExtendWith(MockitoExtension.class)
class LockServiceLoadTest {

    @Mock private EventRepository eventRepository;
    @Mock private SeatRepository seatRepository;
    @Mock private SectionRepository sectionRepository;
    @Mock private SeatLockRepository seatLockRepository;
    @Mock private ZoneLockRepository zoneLockRepository;

    private LockService lockService;

    @BeforeEach
    void setUp() {
        lockService = new LockService(eventRepository, seatRepository, sectionRepository,
            seatLockRepository, zoneLockRepository);
        ReflectionTestUtils.setField(lockService, "lockTtlMinutes", 10);
    }

    @Test
    @DisplayName("Load test: 50 concurrent zone lock requests respecting capacity of 30")
    void givenHighConcurrency_whenAcquireZoneLock_thenTotalDoesNotExceedCapacity() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        int capacity = 30;
        int requestCount = 50;
        int quantityPerRequest = 1;

        Event event = Event.builder().id(eventId).status(EventStatus.PUBLISHED).bookingModel(BookingModel.ZONE).build();
        Section section = Section.builder().id(zoneId).remainingCapacity(capacity).build();

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(sectionRepository.findByIdAndEventIdWithLock(zoneId, eventId)).thenReturn(Optional.of(section));

        AtomicInteger remainingCapacity = new AtomicInteger(capacity);
        when(zoneLockRepository.sumActiveQuantityByZoneId(eq(zoneId), any()))
            .thenAnswer(invocation -> {
                synchronized (remainingCapacity) {
                    int used = capacity - remainingCapacity.get();
                    remainingCapacity.addAndGet(-quantityPerRequest);
                    return used;
                }
            });

        CountDownLatch allDone = new CountDownLatch(requestCount);
        AtomicInteger succeeded = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(20);

        for (int i = 0; i < requestCount; i++) {
            String reservationId = "res-" + i;
            executor.submit(() -> {
                try {
                    lockService.acquireZoneLock(eventId, new LockZoneRequest(reservationId, zoneId, quantityPerRequest));
                    succeeded.incrementAndGet();
                } catch (ZoneCapacityExceededException e) {
                    rejected.incrementAndGet();
                } finally {
                    allDone.countDown();
                }
            });
        }

        boolean finished = allDone.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).as("All threads completed within timeout").isTrue();
        assertThat(succeeded.get()).isEqualTo(capacity);
        assertThat(rejected.get()).isEqualTo(requestCount - capacity);
    }

    @Test
    @DisplayName("Load test: 50 concurrent seat lock requests on the same seat")
    void givenHighConcurrency_whenAcquireSeatLocks_thenOnlyFirstSucceeds() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        int requestCount = 50;

        Event event = Event.builder().id(eventId).status(EventStatus.PUBLISHED).bookingModel(BookingModel.SEAT).build();
        Seat seat = Seat.builder().id(seatId).status(SeatStatus.AVAILABLE).build();

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(seatRepository.findByIdInAndEventIdWithLock(anyList(), eq(eventId))).thenReturn(List.of(seat));

        AtomicInteger lockSlot = new AtomicInteger(0);
        when(seatLockRepository.findBySeatIdInAndExpiresAtAfter(anyList(), any()))
            .thenAnswer(invocation -> {
                int slot = lockSlot.getAndIncrement();
                if (slot == 0) {
                    return List.of();
                }
                return List.of(SeatLock.builder().seatId(seatId).build());
            });

        CountDownLatch allDone = new CountDownLatch(requestCount);
        AtomicInteger succeeded = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(20);

        for (int i = 0; i < requestCount; i++) {
            String reservationId = "res-" + i;
            executor.submit(() -> {
                try {
                    lockService.acquireSeatLocks(eventId, new LockSeatsRequest(reservationId, List.of(seatId)));
                    succeeded.incrementAndGet();
                } catch (Exception e) {
                    rejected.incrementAndGet();
                } finally {
                    allDone.countDown();
                }
            });
        }

        boolean finished = allDone.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).as("All threads completed within timeout").isTrue();
        assertThat(succeeded.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(requestCount - 1);
    }

    @Test
    @DisplayName("Load test: concurrent zone lock + confirm cycle for 30 reservations, capacity 30")
    void givenConcurrentRequests_whenFullZoneLifecycle_thenCapacityNeverNegative() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        int capacity = 30;
        int requestCount = 30;
        int quantityPerRequest = 1;

        Event event = Event.builder().id(eventId).status(EventStatus.PUBLISHED).bookingModel(BookingModel.ZONE).build();
        Section section = Section.builder().id(zoneId).remainingCapacity(capacity).build();

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(sectionRepository.findByIdAndEventIdWithLock(zoneId, eventId)).thenReturn(Optional.of(section));

        AtomicInteger remainingCapacity = new AtomicInteger(capacity);
        when(zoneLockRepository.sumActiveQuantityByZoneId(eq(zoneId), any()))
            .thenAnswer(invocation -> {
                synchronized (remainingCapacity) {
                    return capacity - remainingCapacity.get();
                }
            });

        lenient().when(sectionRepository.findByIdWithLock(zoneId))
            .thenAnswer(invocation -> {
                int remaining = remainingCapacity.get();
                return Optional.of(Section.builder().id(zoneId).remainingCapacity(remaining).build());
            });

        lenient().doAnswer(invocation -> {
            remainingCapacity.addAndGet(-quantityPerRequest);
            return null;
        }).when(zoneLockRepository).deleteByReservationId(anyString());

        CountDownLatch allDone = new CountDownLatch(requestCount * 2);
        ExecutorService executor = Executors.newFixedThreadPool(20);

        for (int i = 0; i < requestCount; i++) {
            String reservationId = "res-" + i;
            executor.submit(() -> {
                try {
                    lockService.acquireZoneLock(eventId, new LockZoneRequest(reservationId, zoneId, quantityPerRequest));
                } catch (Exception ignored) {
                } finally {
                    allDone.countDown();
                }
            });
            String confirmId = "res-" + i;
            executor.submit(() -> {
                try {
                    lockService.confirm(confirmId);
                } catch (Exception ignored) {
                } finally {
                    allDone.countDown();
                }
            });
        }

        boolean finished = allDone.await(15, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(finished).as("All threads completed within timeout").isTrue();

        int finalRemainingCapacity = remainingCapacity.get();
        assertThat(finalRemainingCapacity)
            .as("Remaining capacity must never go below zero after all confirmations")
            .isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("Load test: concurrent seat lock + confirm prevents double booking")
    void givenConcurrentRequests_whenSeatLockAndConfirmRace_thenNoDoubleBooking() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        int requestCount = 20;

        Event event = Event.builder().id(eventId).status(EventStatus.PUBLISHED).bookingModel(BookingModel.SEAT).build();
        Seat seat = Seat.builder().id(seatId).status(SeatStatus.AVAILABLE).build();

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(seatRepository.findByIdInAndEventIdWithLock(anyList(), eq(eventId))).thenReturn(List.of(seat));

        AtomicInteger lockCheckOrder = new AtomicInteger(0);
        when(seatLockRepository.findBySeatIdInAndExpiresAtAfter(anyList(), any()))
            .thenAnswer(invocation -> {
                int callOrder = lockCheckOrder.getAndIncrement();
                if (callOrder == 0) {
                    return List.of();
                }
                return List.of(SeatLock.builder().seatId(seatId).build());
            });

        AtomicInteger bookCount = new AtomicInteger(0);
        lenient().doAnswer(invocation -> {
            bookCount.incrementAndGet();
            return null;
        }).when(seatLockRepository).deleteByReservationId(anyString());

        CountDownLatch allDone = new CountDownLatch(requestCount * 2);
        ExecutorService executor = Executors.newFixedThreadPool(20);

        for (int i = 0; i < requestCount; i++) {
            String reservationId = "res-" + i;
            executor.submit(() -> {
                try {
                    lockService.acquireSeatLocks(eventId, new LockSeatsRequest(reservationId, List.of(seatId)));
                } catch (Exception ignored) {
                } finally {
                    allDone.countDown();
                }
            });
            String confirmId = "res-" + i;
            executor.submit(() -> {
                try {
                    lockService.confirm(confirmId);
                } catch (Exception ignored) {
                } finally {
                    allDone.countDown();
                }
            });
        }

        boolean finished = allDone.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).as("All threads completed within timeout").isTrue();
        assertThat(bookCount.get()).as("At most one reservation can confirm and book the seat").isLessThanOrEqualTo(1);
    }
}
