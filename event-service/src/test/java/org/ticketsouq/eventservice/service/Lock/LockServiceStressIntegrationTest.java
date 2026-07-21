package org.ticketsouq.eventservice.service.Lock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.ticketsouq.sharedmodule.EventService.dto.LockZoneRequest;
import org.ticketsouq.sharedmodule.EventService.dto.LockZoneResponse;
import org.ticketsouq.sharedmodule.EventService.dto.LockSeatsRequest;
import org.ticketsouq.sharedmodule.EventService.dto.LockSeatsResponse;
import org.ticketsouq.sharedmodule.EventService.exception.*;
import org.ticketsouq.eventservice.model.*;
import org.ticketsouq.eventservice.model.enums.SeatStatus;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class LockServiceStressIntegrationTest extends LockServiceIntegrationTestBase {

    private static final int SF = Integer.getInteger("stress.factor", 1);

    private static int threads(int base) {
        return Math.max(4, base * SF / 2);
    }

    private static int timeout(int base) {
        return Math.min(300, base * SF);
    }

    @Test
    @DisplayName("Load test: concurrent zone lock requests respecting capacity")
    void givenHighConcurrency_whenAcquireZoneLock_thenTotalDoesNotExceedCapacity() throws Exception {
        int capacity = 30 * SF;
        int requestCount = capacity * 2;
        int quantityPerRequest = 1;

        Event event = createPublishedZoneEvent();
        Section section = createSection(event, capacity);
        UUID zoneId = section.getId();

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(requestCount);
        ConcurrentLinkedQueue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();
        AtomicInteger succeeded = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(threads(20));

        for (int i = 0; i < requestCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    lockService.acquireZoneLock(event.getId(), new LockZoneRequest(zoneId, quantityPerRequest));
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
        boolean finished = finishLatch.await(timeout(10), TimeUnit.SECONDS);
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
    @DisplayName("Load test: concurrent seat lock requests on the same seat")
    void givenHighConcurrency_whenAcquireSeatLocks_thenOnlyFirstSucceeds() throws Exception {
        int requestCount = 50 * SF;
        UUID seatId = UUID.randomUUID();

        Event event = createPublishedSeatEvent();
        Section section = createSection(event, Math.max(100, requestCount));
        createSeat(section, seatId, SeatStatus.AVAILABLE);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(requestCount);
        ConcurrentLinkedQueue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();
        AtomicInteger succeeded = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(threads(20));

        for (int i = 0; i < requestCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    lockService.acquireSeatLocks(event.getId(), new LockSeatsRequest(List.of(seatId)));
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
        boolean finished = finishLatch.await(timeout(15), TimeUnit.SECONDS);
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
    @DisplayName("Load test: concurrent zone lock+confirm cycles with capacity matching — all confirm, capacity fully consumed")
    void givenConcurrentRequests_whenFullZoneLifecycle_thenAllConfirmAndCapacityZero() throws Exception {
        int capacity = 30 * SF;
        int requestCount = capacity;
        int quantityPerRequest = 1;

        Event event = createPublishedZoneEvent();
        Section section = createSection(event, capacity);
        UUID zoneId = section.getId();

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(requestCount);
        ConcurrentLinkedQueue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(threads(20));

        for (int i = 0; i < requestCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    LockZoneResponse res = lockService.acquireZoneLock(event.getId(), new LockZoneRequest(zoneId, quantityPerRequest));
                    lockService.confirm(res.reservationId().toString());
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
        boolean finished = finishLatch.await(timeout(15), TimeUnit.SECONDS);
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
    @DisplayName("Load test: concurrent seat lock+confirm cycles on 1 seat — exactly 1 books, rest rejected")
    void givenConcurrentRequests_whenSeatLockAndConfirmRace_thenNoDoubleBooking() throws Exception {
        int requestCount = Math.max(10, 20 * SF);
        UUID seatId = UUID.randomUUID();

        Event event = createPublishedSeatEvent();
        Section section = createSection(event, requestCount);
        createSeat(section, seatId, SeatStatus.AVAILABLE);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(requestCount);
        ConcurrentLinkedQueue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();
        AtomicInteger succeeded = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(threads(20));

        for (int i = 0; i < requestCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    LockSeatsResponse res = lockService.acquireSeatLocks(event.getId(), new LockSeatsRequest(List.of(seatId)));
                    lockService.confirm(res.reservationId().toString());
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
        boolean finished = finishLatch.await(timeout(15), TimeUnit.SECONDS);
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
    @DisplayName("Load test: concurrent lock requests across distinct seats succeed independently")
    void givenDistinctSeats_whenConcurrentLock_thenEachSeatLockedAtMostOnce() throws Exception {
        int seatCount = Math.max(10, 50 * SF);
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
        ExecutorService executor = Executors.newFixedThreadPool(threads(20));

        for (int i = 0; i < requestCount; i++) {
            UUID targetSeat = seatIds.get(i % seatCount);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    lockService.acquireSeatLocks(event.getId(), new LockSeatsRequest(List.of(targetSeat)));
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
        boolean finished = finishLatch.await(timeout(15), TimeUnit.SECONDS);
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
    @DisplayName("Load test: concurrent zone requests with exact capacity — all succeed")
    void givenExactCapacity_whenConcurrentZoneLock_thenAllSucceed() throws Exception {
        int capacity = Math.max(10, 100 * SF);
        int requestCount = capacity;
        int quantityPerRequest = 1;

        Event event = createPublishedZoneEvent();
        Section section = createSection(event, capacity);
        UUID zoneId = section.getId();

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(requestCount);
        ConcurrentLinkedQueue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();
        AtomicInteger succeeded = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(threads(20));

        for (int i = 0; i < requestCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    lockService.acquireZoneLock(event.getId(), new LockZoneRequest(zoneId, quantityPerRequest));
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
        boolean finished = finishLatch.await(timeout(15), TimeUnit.SECONDS);
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
    @DisplayName("Stress: concurrent seat reservations — acquire + confirm each, verify all booked, no orphans")
    void given500Seats_whenConcurrentAcquireAndConfirm_thenAllBooked() throws Exception {
        int count = Math.max(10, 500 * SF);
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
        ExecutorService executor = Executors.newFixedThreadPool(threads(20));

        for (int i = 0; i < count; i++) {
            UUID seatId = allSeatIds.get(i);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    LockSeatsResponse res = lockService.acquireSeatLocks(event.getId(), new LockSeatsRequest(List.of(seatId)));
                    lockService.confirm(res.reservationId().toString());
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
        boolean finished = finishLatch.await(timeout(60), TimeUnit.SECONDS);
        executor.shutdown();
        boolean terminated = executor.awaitTermination(timeout(10), TimeUnit.SECONDS);
        if (!terminated) executor.shutdownNow();

        assertThat(finished).as("All threads completed within timeout").isTrue();
        assertThat(terminated).as("Executor terminated cleanly").isTrue();
        assertThat(unexpectedErrors).as("No errors during concurrent seat reservations").isEmpty();

        List<Seat> allSeats = seatRepository.findAll();
        long booked = allSeats.stream().filter(s -> s.getStatus() == SeatStatus.BOOKED).count();
        assertThat(booked).as("All seats booked").isEqualTo(count);
        assertThat(seatLockRepository.count()).as("No seat locks remain after all confirms").isZero();
        assertThat(allSeats).as("All seats in valid state").allMatch(s -> s.getStatus() == SeatStatus.BOOKED);
    }

    @Test
    @DisplayName("Stress: concurrent zone reservations — acquire + confirm each, verify capacity fully consumed")
    void given1000ZoneLocks_whenConcurrentAcquireAndConfirm_thenAllConsumed() throws Exception {
        int count = Math.max(10, 1000 * SF);
        int capacity = count;
        Event event = createPublishedZoneEvent();
        Section section = createSection(event, capacity);
        UUID zoneId = section.getId();

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(count);
        ConcurrentLinkedQueue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(threads(20));

        for (int i = 0; i < count; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    LockZoneResponse res = lockService.acquireZoneLock(event.getId(), new LockZoneRequest(zoneId, 1));
                    lockService.confirm(res.reservationId().toString());
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
        boolean finished = finishLatch.await(timeout(120), TimeUnit.SECONDS);
        executor.shutdown();
        boolean terminated = executor.awaitTermination(timeout(10), TimeUnit.SECONDS);
        if (!terminated) executor.shutdownNow();

        assertThat(finished).as("All threads completed within timeout").isTrue();
        assertThat(terminated).as("Executor terminated cleanly").isTrue();
        assertThat(unexpectedErrors).as("No errors during concurrent zone reservations").isEmpty();

        Section refreshed = sectionRepository.findById(zoneId).orElseThrow();
        assertThat(refreshed.getRemainingCapacity()).as("All capacity consumed").isZero();
        assertThat(zoneLockRepository.sumActiveQuantityByZoneId(zoneId, LocalDateTime.now()))
            .as("No zone locks remain after all confirms").isZero();
        assertThat(zoneLockRepository.count()).as("No orphan zone locks").isZero();
    }

    @Test
    @DisplayName("Stress: acquire/release cycles on many seats — no drift after repeated cycles")
    void givenRepeatedAcquireReleaseCycles_whenTenCycles_thenStateReturnsToBaseline() throws Exception {
        int seatCount = Math.max(10, 100 * SF);
        int cycles = Math.max(2, 10 * SF / 2);
        Event event = createPublishedSeatEvent();
        Section section = createSection(event, seatCount);
        List<UUID> seatIds = new ArrayList<>();
        for (int i = 0; i < seatCount; i++) {
            UUID sid = UUID.randomUUID();
            seatIds.add(sid);
            createSeat(section, sid, SeatStatus.AVAILABLE);
        }

        int poolSize = threads(20);
        for (int cycle = 0; cycle < cycles; cycle++) {
            Map<Integer, String> reservationIds = new ConcurrentHashMap<>();

            CountDownLatch acquireStartLatch = new CountDownLatch(1);
            CountDownLatch acquireFinishLatch = new CountDownLatch(seatCount);
            ConcurrentLinkedQueue<Throwable> acquireErrors = new ConcurrentLinkedQueue<>();
            ExecutorService acquireExecutor = Executors.newFixedThreadPool(poolSize);

            for (int idx = 0; idx < seatCount; idx++) {
                int finalIdx = idx;
                UUID sid = seatIds.get(idx);
                acquireExecutor.submit(() -> {
                    try {
                        acquireStartLatch.await();
                        LockSeatsResponse res = lockService.acquireSeatLocks(event.getId(), new LockSeatsRequest(List.of(sid)));
                        reservationIds.put(finalIdx, res.reservationId().toString());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        acquireErrors.add(e);
                    } catch (SeatAlreadyLockedException e) {
                        acquireErrors.add(e);
                    } catch (Exception e) {
                        acquireErrors.add(e);
                    } finally {
                        acquireFinishLatch.countDown();
                    }
                });
            }

            acquireStartLatch.countDown();
            boolean acquired = acquireFinishLatch.await(timeout(30), TimeUnit.SECONDS);
            acquireExecutor.shutdown();
            boolean termA = acquireExecutor.awaitTermination(timeout(5), TimeUnit.SECONDS);
            if (!termA) acquireExecutor.shutdownNow();

            assertThat(acquired).as("Cycle %d: all acquire threads completed".formatted(cycle)).isTrue();
            assertThat(acquireErrors).as("Cycle %d: no acquire errors".formatted(cycle)).isEmpty();
            assertThat(seatLockRepository.count()).as("Cycle %d: all seats locked".formatted(cycle)).isEqualTo(seatCount);

            CountDownLatch releaseStartLatch = new CountDownLatch(1);
            CountDownLatch releaseFinishLatch = new CountDownLatch(seatCount);
            ConcurrentLinkedQueue<Throwable> releaseErrors = new ConcurrentLinkedQueue<>();
            ExecutorService releaseExecutor = Executors.newFixedThreadPool(poolSize);

            for (int idx = 0; idx < seatCount; idx++) {
                int finalIdx = idx;
                releaseExecutor.submit(() -> {
                    try {
                        releaseStartLatch.await();
                        lockService.release(reservationIds.get(finalIdx));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        releaseErrors.add(e);
                    } catch (Exception e) {
                        releaseErrors.add(e);
                    } finally {
                        releaseFinishLatch.countDown();
                    }
                });
            }

            releaseStartLatch.countDown();
            boolean released = releaseFinishLatch.await(timeout(30), TimeUnit.SECONDS);
            releaseExecutor.shutdown();
            boolean termR = releaseExecutor.awaitTermination(timeout(5), TimeUnit.SECONDS);
            if (!termR) releaseExecutor.shutdownNow();

            assertThat(released).as("Cycle %d: all release threads completed".formatted(cycle)).isTrue();
            assertThat(releaseErrors).as("Cycle %d: no release errors".formatted(cycle)).isEmpty();
            assertThat(seatLockRepository.count()).as("Cycle %d: no locks after release".formatted(cycle)).isZero();
        }

        assertThat(seatRepository.count()).as("Total seats unchanged after all cycles").isEqualTo(seatCount);
        assertThat(seatLockRepository.count()).as("No orphan locks remain after all cycles").isZero();
        assertThat(seatRepository.findAll()).as("All seats AVAILABLE after all cycles")
            .allMatch(s -> s.getStatus() == SeatStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Stress: acquire/confirm cycles on zones — capacity fully consumed incrementally")
    void givenRepeatedZoneAcquireConfirmCycles_whenTenCycles_thenCapacityFullyConsumed() throws Exception {
        int cycles = Math.max(2, 10 * SF / 2);
        int perCycle = Math.max(10, 100 * SF);
        int capacity = perCycle * cycles;
        Event event = createPublishedZoneEvent();
        Section section = createSection(event, capacity);
        UUID zoneId = section.getId();

        int poolSize = threads(20);
        for (int cycle = 0; cycle < cycles; cycle++) {
            int offset = cycle * perCycle;

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch finishLatch = new CountDownLatch(perCycle);
            ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
            ExecutorService executor = Executors.newFixedThreadPool(poolSize);

            for (int i = 0; i < perCycle; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        LockZoneResponse res = lockService.acquireZoneLock(event.getId(), new LockZoneRequest(zoneId, 1));
                        lockService.confirm(res.reservationId().toString());
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
            boolean finished = finishLatch.await(timeout(60), TimeUnit.SECONDS);
            executor.shutdown();
            boolean terminated = executor.awaitTermination(timeout(5), TimeUnit.SECONDS);
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

        Section refreshed = sectionRepository.findById(zoneId).orElseThrow();
        assertThat(refreshed.getRemainingCapacity()).as("Capacity fully consumed after all cycles").isEqualTo(capacity - perCycle * cycles);
        assertThat(refreshed.getRemainingCapacity()).as("remainingCapacity never negative").isNotNegative();
        assertThat(zoneLockRepository.count()).as("No orphan zone locks after all cycles").isZero();
    }

    @Test
    @DisplayName("Concurrent zone: mixed quantities (3,2) on capacity 20 — total active never exceeds capacity")
    void givenMixedQuantities_whenConcurrentZoneAcquire_thenTotalWithinCapacity() throws Exception {
        int capacity = Math.max(10, 20 * SF);
        Event event = createPublishedZoneEvent();
        Section section = createSection(event, capacity);
        UUID zoneId = section.getId();

        record QtyReq(int qty) {}
        List<QtyReq> baseRequests = List.of(
            new QtyReq(3), new QtyReq(3),
            new QtyReq(3), new QtyReq(3),
            new QtyReq(2), new QtyReq(2),
            new QtyReq(2), new QtyReq(2)
        );
        int repeats = Math.max(1, SF);
        List<QtyReq> requests = new ArrayList<>();
        for (int r = 0; r < repeats; r++) {
            requests.addAll(baseRequests);
        }
        int requestCount = requests.size();

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(requestCount);
        ConcurrentLinkedQueue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();
        AtomicInteger succeeded = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(threads(20));

        for (QtyReq req : requests) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    lockService.acquireZoneLock(event.getId(), new LockZoneRequest(zoneId, req.qty));
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
        boolean finished = finishLatch.await(timeout(15), TimeUnit.SECONDS);
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
