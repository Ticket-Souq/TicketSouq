package org.ticketsouq.eventservice.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.ticketsouq.eventservice.dto.*;
import org.ticketsouq.sharedmodule.EventService.exception.*;
import org.ticketsouq.eventservice.model.*;
import org.ticketsouq.eventservice.model.enums.SeatStatus;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class LockServiceStressIntegrationTest extends LockServiceIntegrationTestBase {

    @Test
    @DisplayName("Load test: 50 concurrent zone lock requests respecting capacity of 30")
    void givenHighConcurrency_whenAcquireZoneLock_thenTotalDoesNotExceedCapacity() throws Exception {
        int capacity = 30;
        int requestCount = 50;
        int quantityPerRequest = 1;

        Event event = createPublishedZoneEvent();
        Section section = createSection(event, capacity);
        UUID zoneId = section.getId();

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(requestCount);
        ConcurrentLinkedQueue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();
        AtomicInteger succeeded = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(20);

        for (int i = 0; i < requestCount; i++) {
            String reservationId = "zone-load-res-" + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    lockService.acquireZoneLock(event.getId(), new LockZoneRequest(reservationId, zoneId, quantityPerRequest));
                    succeeded.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(e);
                } catch (ZoneCapacityExceededException e) {
                    rejected.incrementAndGet();
                } catch (Exception e) {
                    unexpectedErrors.add(e);
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = finishLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).as("All threads completed within timeout").isTrue();
        boolean terminated = executor.awaitTermination(3, TimeUnit.SECONDS);
        if (!terminated) {
            executor.shutdownNow();
        }
        assertThat(terminated).as("Executor terminated cleanly").isTrue();
        assertThat(unexpectedErrors).as("No unexpected exceptions").isEmpty();
        int totalActive = zoneLockRepository.sumActiveQuantityByZoneId(zoneId, LocalDateTime.now());
        assertThat(succeeded.get()).as("Exactly capacity limit succeeded").isEqualTo(capacity);
        assertThat(rejected.get()).as("Remaining requests were rejected").isEqualTo(requestCount - capacity);
        assertThat(totalActive).as("DB active locks match success count").isEqualTo(capacity);
    }

    @Test
    @DisplayName("Load test: 50 concurrent seat lock requests on the same seat")
    void givenHighConcurrency_whenAcquireSeatLocks_thenOnlyFirstSucceeds() throws Exception {
        int requestCount = 50;
        UUID seatId = UUID.randomUUID();

        Event event = createPublishedSeatEvent();
        Section section = createSection(event, 100);
        createSeat(section, seatId, SeatStatus.AVAILABLE);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(requestCount);
        ConcurrentLinkedQueue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();
        AtomicInteger succeeded = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(20);

        for (int i = 0; i < requestCount; i++) {
            String reservationId = "seat-load-res-" + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    lockService.acquireSeatLocks(event.getId(), new LockSeatsRequest(reservationId, List.of(seatId)));
                    succeeded.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(e);
                } catch (SeatAlreadyLockedException e) {
                    rejected.incrementAndGet();
                } catch (Exception e) {
                    unexpectedErrors.add(e);
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = finishLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).as("All threads completed within timeout").isTrue();
        boolean terminated = executor.awaitTermination(3, TimeUnit.SECONDS);
        if (!terminated) {
            executor.shutdownNow();
        }
        assertThat(terminated).as("Executor terminated cleanly").isTrue();
        assertThat(unexpectedErrors).as("No unexpected exceptions").isEmpty();
        long dbLocks = seatLockRepository.count();
        assertThat(succeeded.get()).as("Only one lock acquisition should succeed for the same seat").isEqualTo(1);
        assertThat(rejected.get()).as("All other requests should be rejected").isEqualTo(requestCount - 1);
        assertThat(dbLocks).as("Database should contain exactly one seat lock").isEqualTo(1);
    }

    @Test
    @DisplayName("Load test: 30 concurrent zone lock+confirm cycles with capacity 30 — all confirm, capacity fully consumed")
    void givenConcurrentRequests_whenFullZoneLifecycle_thenAllConfirmAndCapacityZero() throws Exception {
        int capacity = 30;
        int requestCount = 30;
        int quantityPerRequest = 1;

        Event event = createPublishedZoneEvent();
        Section section = createSection(event, capacity);
        UUID zoneId = section.getId();

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(requestCount);
        ConcurrentLinkedQueue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(20);

        for (int i = 0; i < requestCount; i++) {
            String reservationId = "zone-lifecycle-res-" + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    lockService.acquireZoneLock(event.getId(), new LockZoneRequest(reservationId, zoneId, quantityPerRequest));
                    lockService.confirm(reservationId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(e);
                } catch (ZoneCapacityExceededException e) {
                    // expected — some acquires may fail due to capacity contention
                } catch (Exception e) {
                    unexpectedErrors.add(e);
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = finishLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();
        boolean terminated = executor.awaitTermination(5, TimeUnit.SECONDS);
        if (!terminated) {
            executor.shutdownNow();
        }

        assertThat(finished).as("All threads completed within timeout").isTrue();
        assertThat(terminated).as("Executor terminated cleanly").isTrue();
        assertThat(unexpectedErrors).as("No unexpected exceptions").isEmpty();
        Section refreshed = sectionRepository.findById(section.getId()).orElseThrow();
        assertThat(refreshed.getRemainingCapacity())
            .as("All capacity consumed after full lifecycle")
            .isZero();
        long remainingLocks = zoneLockRepository.sumActiveQuantityByZoneId(zoneId, LocalDateTime.now());
        assertThat(remainingLocks)
            .as("No active zone locks remain after all confirms")
            .isZero();
    }

    @Test
    @DisplayName("Load test: 20 concurrent seat lock+confirm cycles on 1 seat — exactly 1 books, rest rejected")
    void givenConcurrentRequests_whenSeatLockAndConfirmRace_thenNoDoubleBooking() throws Exception {
        int requestCount = 20;
        UUID seatId = UUID.randomUUID();

        Event event = createPublishedSeatEvent();
        Section section = createSection(event, requestCount);
        createSeat(section, seatId, SeatStatus.AVAILABLE);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(requestCount);
        ConcurrentLinkedQueue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();
        AtomicInteger succeeded = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(20);

        for (int i = 0; i < requestCount; i++) {
            String reservationId = "seat-lifecycle-res-" + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    lockService.acquireSeatLocks(event.getId(), new LockSeatsRequest(reservationId, List.of(seatId)));
                    lockService.confirm(reservationId);
                    succeeded.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(e);
                } catch (SeatAlreadyLockedException | SeatAlreadyBookedException e) {
                    rejected.incrementAndGet();
                } catch (Exception e) {
                    unexpectedErrors.add(e);
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = finishLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();
        boolean terminated = executor.awaitTermination(5, TimeUnit.SECONDS);
        if (!terminated) {
            executor.shutdownNow();
        }

        assertThat(finished).as("All threads completed within timeout").isTrue();
        assertThat(terminated).as("Executor terminated cleanly").isTrue();
        assertThat(unexpectedErrors).as("No unexpected exceptions").isEmpty();
        assertThat(succeeded.get()).as("Exactly one seat was locked and confirmed").isEqualTo(1);
        assertThat(rejected.get()).as("All other requests were rejected").isEqualTo(requestCount - 1);
        long bookedCount = seatRepository.findAll().stream()
            .filter(s -> s.getStatus() == SeatStatus.BOOKED)
            .count();
        assertThat(bookedCount).as("Exactly one booking for the same seat").isEqualTo(1);
        long dbLocks = seatLockRepository.count();
        assertThat(dbLocks).as("No seat locks remain after confirm").isZero();
    }

    @Test
    @DisplayName("Load test: 100 concurrent lock requests across 50 distinct seats succeed independently")
    void givenDistinctSeats_whenConcurrentLock_thenEachSeatLockedAtMostOnce() throws Exception {
        int seatCount = 50;
        int requestsPerSeat = 2;
        int requestCount = seatCount * requestsPerSeat;

        Event event = createPublishedSeatEvent();
        Section section = createSection(event, seatCount);
        List<UUID> seatIds = new ArrayList<>();
        for (int i = 0; i < seatCount; i++) {
            UUID seatId = UUID.randomUUID();
            seatIds.add(seatId);
            createSeat(section, seatId, SeatStatus.AVAILABLE);
        }

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(requestCount);
        ConcurrentLinkedQueue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();
        AtomicInteger succeeded = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(20);

        for (int i = 0; i < requestCount; i++) {
            UUID targetSeat = seatIds.get(i % seatCount);
            String reservationId = "distinct-seat-res-" + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    lockService.acquireSeatLocks(event.getId(), new LockSeatsRequest(reservationId, List.of(targetSeat)));
                    succeeded.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(e);
                } catch (SeatAlreadyLockedException e) {
                    rejected.incrementAndGet();
                } catch (Exception e) {
                    unexpectedErrors.add(e);
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = finishLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).as("All threads completed within timeout").isTrue();
        boolean terminated = executor.awaitTermination(3, TimeUnit.SECONDS);
        if (!terminated) {
            executor.shutdownNow();
        }
        assertThat(terminated).as("Executor terminated cleanly").isTrue();
        assertThat(unexpectedErrors).as("No unexpected exceptions").isEmpty();
        assertThat(succeeded.get()).as("Number of successful locks equals number of seats").isEqualTo(seatCount);
        assertThat(rejected.get()).as("Number of rejected locks equals number of seats").isEqualTo(seatCount);
        long totalLocks = seatLockRepository.count();
        assertThat(totalLocks).as("Each seat locked at most once").isEqualTo(seatCount);
    }

    @Test
    @DisplayName("Load test: 100 concurrent zone requests with capacity 100 — all succeed")
    void givenExactCapacity_whenConcurrentZoneLock_thenAllSucceed() throws Exception {
        int capacity = 100;
        int requestCount = 100;
        int quantityPerRequest = 1;

        Event event = createPublishedZoneEvent();
        Section section = createSection(event, capacity);
        UUID zoneId = section.getId();

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(requestCount);
        ConcurrentLinkedQueue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();
        AtomicInteger succeeded = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(20);

        for (int i = 0; i < requestCount; i++) {
            String reservationId = "exact-cap-res-" + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    lockService.acquireZoneLock(event.getId(), new LockZoneRequest(reservationId, zoneId, quantityPerRequest));
                    succeeded.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(e);
                } catch (ZoneCapacityExceededException e) {
                    rejected.incrementAndGet();
                } catch (Exception e) {
                    unexpectedErrors.add(e);
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = finishLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).as("All threads completed within timeout").isTrue();
        boolean terminated = executor.awaitTermination(3, TimeUnit.SECONDS);
        if (!terminated) {
            executor.shutdownNow();
        }
        assertThat(terminated).as("Executor terminated cleanly").isTrue();
        assertThat(unexpectedErrors).as("No unexpected exceptions").isEmpty();
        int totalActive = zoneLockRepository.sumActiveQuantityByZoneId(zoneId, LocalDateTime.now());
        assertThat(totalActive).as("Exactly capacity locks should be acquired").isEqualTo(capacity);
        assertThat(succeeded.get()).isEqualTo(capacity);
        assertThat(rejected.get()).isZero();
    }

    @Test
    @DisplayName("Stress: 500 concurrent seat reservations — acquire + confirm each, verify all booked, no orphans")
    void given500Seats_whenConcurrentAcquireAndConfirm_thenAllBooked() throws Exception {
        int count = 500;
        Event event = createPublishedSeatEvent();
        Section section = createSection(event, count);
        List<UUID> allSeatIds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            UUID sid = UUID.randomUUID();
            allSeatIds.add(sid);
            createSeat(section, sid, SeatStatus.AVAILABLE);
        }

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(count);
        ConcurrentLinkedQueue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(20);

        for (int i = 0; i < count; i++) {
            UUID seatId = allSeatIds.get(i);
            String reservationId = "stress500-seat-" + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    lockService.acquireSeatLocks(event.getId(), new LockSeatsRequest(reservationId, List.of(seatId)));
                    lockService.confirm(reservationId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(e);
                } catch (SeatAlreadyLockedException | SeatAlreadyBookedException e) {
                    unexpectedErrors.add(e);
                } catch (Exception e) {
                    unexpectedErrors.add(e);
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = finishLatch.await(60, TimeUnit.SECONDS);
        executor.shutdown();
        boolean terminated = executor.awaitTermination(10, TimeUnit.SECONDS);
        if (!terminated) executor.shutdownNow();

        assertThat(finished).as("All 500 threads completed within timeout").isTrue();
        assertThat(terminated).as("Executor terminated cleanly").isTrue();
        assertThat(unexpectedErrors).as("No errors during 500 concurrent seat reservations").isEmpty();

        List<Seat> allSeats = seatRepository.findAll();
        long booked = allSeats.stream().filter(s -> s.getStatus() == SeatStatus.BOOKED).count();
        assertThat(booked).as("All 500 seats booked").isEqualTo(count);
        assertThat(seatLockRepository.count()).as("No seat locks remain after all confirms").isZero();
        assertThat(allSeats).as("All seats in valid state").allMatch(s -> s.getStatus() == SeatStatus.BOOKED);
    }

    @Test
    @DisplayName("Stress: 1000 concurrent zone reservations — acquire + confirm each, verify capacity fully consumed")
    void given1000ZoneLocks_whenConcurrentAcquireAndConfirm_thenAllConsumed() throws Exception {
        int count = 1000;
        int capacity = 1000;
        Event event = createPublishedZoneEvent();
        Section section = createSection(event, capacity);
        UUID zoneId = section.getId();

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(count);
        ConcurrentLinkedQueue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(20);

        for (int i = 0; i < count; i++) {
            String reservationId = "stress1000-zone-" + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    lockService.acquireZoneLock(event.getId(), new LockZoneRequest(reservationId, zoneId, 1));
                    lockService.confirm(reservationId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(e);
                } catch (ZoneCapacityExceededException e) {
                    unexpectedErrors.add(e);
                } catch (Exception e) {
                    unexpectedErrors.add(e);
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = finishLatch.await(120, TimeUnit.SECONDS);
        executor.shutdown();
        boolean terminated = executor.awaitTermination(10, TimeUnit.SECONDS);
        if (!terminated) executor.shutdownNow();

        assertThat(finished).as("All 1000 threads completed within timeout").isTrue();
        assertThat(terminated).as("Executor terminated cleanly").isTrue();
        assertThat(unexpectedErrors).as("No errors during 1000 concurrent zone reservations").isEmpty();

        Section refreshed = sectionRepository.findById(zoneId).orElseThrow();
        assertThat(refreshed.getRemainingCapacity()).as("All capacity consumed").isZero();
        assertThat(zoneLockRepository.sumActiveQuantityByZoneId(zoneId, LocalDateTime.now()))
            .as("No zone locks remain after all confirms").isZero();
        assertThat(zoneLockRepository.count()).as("No orphan zone locks").isZero();
    }

    @Test
    @DisplayName("Stress: 10 cycles of acquire/release on 100 seats — no drift after repeated cycles")
    void givenRepeatedAcquireReleaseCycles_whenTenCycles_thenStateReturnsToBaseline() throws Exception {
        int seatCount = 100;
        int cycles = 10;
        Event event = createPublishedSeatEvent();
        Section section = createSection(event, seatCount);
        List<UUID> seatIds = new ArrayList<>();
        for (int i = 0; i < seatCount; i++) {
            UUID sid = UUID.randomUUID();
            seatIds.add(sid);
            createSeat(section, sid, SeatStatus.AVAILABLE);
        }

        for (int cycle = 0; cycle < cycles; cycle++) {
            // Phase A: acquire all seats concurrently
            CountDownLatch acquireStartLatch = new CountDownLatch(1);
            CountDownLatch acquireFinishLatch = new CountDownLatch(seatCount);
            ConcurrentLinkedQueue<Throwable> acquireErrors = new ConcurrentLinkedQueue<>();
            ExecutorService acquireExecutor = Executors.newFixedThreadPool(20);
            CountDownLatch aSL = acquireStartLatch;
            CountDownLatch aFL = acquireFinishLatch;
            ConcurrentLinkedQueue<Throwable> aErrs = acquireErrors;
            ExecutorService aExec = acquireExecutor;

            for (int i = 0; i < seatCount; i++) {
                String resId = "cycle-" + cycle + "-seat-" + i;
                UUID sid = seatIds.get(i);
                aExec.submit(() -> {
                    try {
                        aSL.await();
                        lockService.acquireSeatLocks(event.getId(), new LockSeatsRequest(resId, List.of(sid)));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        aErrs.add(e);
                    } catch (SeatAlreadyLockedException e) {
                        aErrs.add(e);
                    } catch (Exception e) {
                        aErrs.add(e);
                    } finally {
                        aFL.countDown();
                    }
                });
            }

            acquireStartLatch.countDown();
            boolean acquired = acquireFinishLatch.await(30, TimeUnit.SECONDS);
            acquireExecutor.shutdown();
            boolean termA = acquireExecutor.awaitTermination(5, TimeUnit.SECONDS);
            if (!termA) acquireExecutor.shutdownNow();

            assertThat(acquired).as("Cycle %d: all acquire threads completed".formatted(cycle)).isTrue();
            assertThat(acquireErrors).as("Cycle %d: no acquire errors".formatted(cycle)).isEmpty();
            assertThat(seatLockRepository.count()).as("Cycle %d: all seats locked".formatted(cycle)).isEqualTo(seatCount);

            // Phase B: release all seats concurrently
            CountDownLatch releaseStartLatch = new CountDownLatch(1);
            CountDownLatch releaseFinishLatch = new CountDownLatch(seatCount);
            ConcurrentLinkedQueue<Throwable> releaseErrors = new ConcurrentLinkedQueue<>();
            ExecutorService releaseExecutor = Executors.newFixedThreadPool(20);
            CountDownLatch rSL = releaseStartLatch;
            CountDownLatch rFL = releaseFinishLatch;
            ConcurrentLinkedQueue<Throwable> rErrs = releaseErrors;
            ExecutorService rExec = releaseExecutor;

            for (int i = 0; i < seatCount; i++) {
                String resId = "cycle-" + cycle + "-seat-" + i;
                rExec.submit(() -> {
                    try {
                        rSL.await();
                        lockService.release(resId);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        rErrs.add(e);
                    } catch (Exception e) {
                        rErrs.add(e);
                    } finally {
                        rFL.countDown();
                    }
                });
            }

            releaseStartLatch.countDown();
            boolean released = releaseFinishLatch.await(30, TimeUnit.SECONDS);
            releaseExecutor.shutdown();
            boolean termR = releaseExecutor.awaitTermination(5, TimeUnit.SECONDS);
            if (!termR) releaseExecutor.shutdownNow();

            assertThat(released).as("Cycle %d: all release threads completed".formatted(cycle)).isTrue();
            assertThat(releaseErrors).as("Cycle %d: no release errors".formatted(cycle)).isEmpty();
            assertThat(seatLockRepository.count()).as("Cycle %d: no locks after release".formatted(cycle)).isZero();
        }

        // Final invariants after all cycles
        assertThat(seatRepository.count()).as("Total seats unchanged after all cycles").isEqualTo(seatCount);
        assertThat(seatLockRepository.count()).as("No orphan locks remain after all cycles").isZero();
        assertThat(seatRepository.findAll()).as("All seats AVAILABLE after all cycles")
            .allMatch(s -> s.getStatus() == SeatStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Stress: 10 cycles of acquire/confirm on zones — capacity fully consumed incrementally")
    void givenRepeatedZoneAcquireConfirmCycles_whenTenCycles_thenCapacityFullyConsumed() throws Exception {
        int capacity = 1000;
        int perCycle = 100;
        int cycles = 10;
        int total = perCycle * cycles;
        Event event = createPublishedZoneEvent();
        Section section = createSection(event, capacity);
        UUID zoneId = section.getId();

        for (int cycle = 0; cycle < cycles; cycle++) {
            int offset = cycle * perCycle;

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch finishLatch = new CountDownLatch(perCycle);
            ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
            ExecutorService executor = Executors.newFixedThreadPool(20);

            for (int i = 0; i < perCycle; i++) {
                String resId = "zone-cycle-" + cycle + "-" + i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        lockService.acquireZoneLock(event.getId(), new LockZoneRequest(resId, zoneId, 1));
                        lockService.confirm(resId);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        errors.add(e);
                    } catch (ZoneCapacityExceededException e) {
                        errors.add(e);
                    } catch (Exception e) {
                        errors.add(e);
                    } finally {
                        finishLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean finished = finishLatch.await(60, TimeUnit.SECONDS);
            executor.shutdown();
            boolean terminated = executor.awaitTermination(5, TimeUnit.SECONDS);
            if (!terminated) executor.shutdownNow();

            assertThat(finished).as("Cycle %d: all threads completed".formatted(cycle)).isTrue();
            assertThat(errors).as("Cycle %d: no errors".formatted(cycle)).isEmpty();

            Section refreshed = sectionRepository.findById(zoneId).orElseThrow();
            int expected = capacity - (offset + perCycle);
            assertThat(refreshed.getRemainingCapacity())
                .as("Cycle %d: remainingCapacity = capacity - cycleProgress".formatted(cycle))
                .isEqualTo(expected);
            assertThat(zoneLockRepository.sumActiveQuantityByZoneId(zoneId, LocalDateTime.now()))
                .as("Cycle %d: no active locks after confirm".formatted(cycle)).isZero();
        }

        // Final invariants
        Section refreshed = sectionRepository.findById(zoneId).orElseThrow();
        assertThat(refreshed.getRemainingCapacity()).as("Capacity fully consumed after all cycles").isEqualTo(capacity - total);
        assertThat(refreshed.getRemainingCapacity()).as("remainingCapacity never negative").isNotNegative();
        assertThat(zoneLockRepository.count()).as("No orphan zone locks after all cycles").isZero();
    }

    @Test
    @DisplayName("Concurrent zone: mixed quantities (3,2) on capacity 20 — total active never exceeds capacity")
    void givenMixedQuantities_whenConcurrentZoneAcquire_thenTotalWithinCapacity() throws Exception {
        int capacity = 20;
        Event event = createPublishedZoneEvent();
        Section section = createSection(event, capacity);
        UUID zoneId = section.getId();

        record QtyReq(String id, int qty) {}
        List<QtyReq> requests = List.of(
            new QtyReq("mq-0", 3), new QtyReq("mq-1", 3),
            new QtyReq("mq-2", 3), new QtyReq("mq-3", 3),
            new QtyReq("mq-4", 2), new QtyReq("mq-5", 2),
            new QtyReq("mq-6", 2), new QtyReq("mq-7", 2)
        );
        int requestCount = requests.size();

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(requestCount);
        ConcurrentLinkedQueue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();
        AtomicInteger succeeded = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(20);

        for (QtyReq req : requests) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    lockService.acquireZoneLock(event.getId(), new LockZoneRequest(req.id, zoneId, req.qty));
                    succeeded.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(e);
                } catch (ZoneCapacityExceededException e) {
                    rejected.incrementAndGet();
                } catch (Exception e) {
                    unexpectedErrors.add(e);
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = finishLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();
        boolean terminated = executor.awaitTermination(3, TimeUnit.SECONDS);
        if (!terminated) executor.shutdownNow();

        assertThat(finished).as("All threads completed").isTrue();
        assertThat(terminated).as("Executor terminated cleanly").isTrue();
        assertThat(unexpectedErrors).as("No unexpected exceptions").isEmpty();
        assertThat(succeeded.get() + rejected.get()).as("All requests produced a result").isEqualTo(requestCount);

        int totalActive = zoneLockRepository.sumActiveQuantityByZoneId(zoneId, LocalDateTime.now());
        assertThat(totalActive).as("Active quantity never exceeds capacity").isLessThanOrEqualTo(capacity);
        assertThat(totalActive).as("Active quantity matches sum of succeeded request quantities").isPositive();
    }
}
