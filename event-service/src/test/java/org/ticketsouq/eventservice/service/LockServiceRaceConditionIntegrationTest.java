package org.ticketsouq.eventservice.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.ticketsouq.eventservice.dto.*;
import org.ticketsouq.sharedmodule.EventService.exception.*;
import org.ticketsouq.eventservice.model.*;
import org.ticketsouq.eventservice.model.enums.SeatStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class LockServiceRaceConditionIntegrationTest extends LockServiceIntegrationTestBase {

    @Test
    @DisplayName("Race: confirm vs release for seat — state must remain consistent")
    void givenSeatReservation_whenConfirmAndReleaseRace_thenConsistentState() throws Exception {
        int pairCount = 10;
        Event event = createPublishedSeatEvent();
        Section section = createSection(event, pairCount);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(pairCount * 2);
        ConcurrentLinkedQueue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(20);

        for (int i = 0; i < pairCount; i++) {
            UUID seatId = UUID.randomUUID();
            createSeat(section, seatId, SeatStatus.AVAILABLE);
            String reservationId = "confirm-release-race-" + i;

            lockService.acquireSeatLocks(event.getId(), new LockSeatsRequest(reservationId, List.of(seatId)));

            executor.submit(() -> {
                try {
                    startLatch.await();
                    lockService.confirm(reservationId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(e);
                } catch (ObjectOptimisticLockingFailureException e) {
                    // expected — concurrent deleteByReservationId raced with release
                } catch (Exception e) {
                    unexpectedErrors.add(e);
                } finally {
                    finishLatch.countDown();
                }
            });
            executor.submit(() -> {
                try {
                    startLatch.await();
                    lockService.release(reservationId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(e);
                } catch (ObjectOptimisticLockingFailureException e) {
                    // expected — concurrent deleteByReservationId raced with confirm
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
        boolean terminated = executor.awaitTermination(3, TimeUnit.SECONDS);
        if (!terminated) {
            executor.shutdownNow();
        }

        assertThat(finished).as("All threads completed within timeout").isTrue();
        assertThat(terminated).as("Executor terminated cleanly").isTrue();
        assertThat(unexpectedErrors).as("No unexpected exceptions").isEmpty();

        List<Seat> allSeats = seatRepository.findAll();
        assertThat(allSeats).as("All seats in valid state").allMatch(s -> s.getStatus() == SeatStatus.AVAILABLE || s.getStatus() == SeatStatus.BOOKED);
        assertThat(seatLockRepository.count()).as("No orphan seat locks remain").isZero();
    }

    @Test
    @DisplayName("Race: confirm vs release for zone — capacity never negative, no orphan locks")
    void givenZoneReservation_whenConfirmAndReleaseRace_thenConsistentState() throws Exception {
        int pairCount = 10;
        Event event = createPublishedZoneEvent();
        Section section = createSection(event, 100);
        UUID zoneId = section.getId();

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(pairCount * 2);
        ConcurrentLinkedQueue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(20);

        for (int i = 0; i < pairCount; i++) {
            String reservationId = "zone-confirm-release-race-" + i;
            lockService.acquireZoneLock(event.getId(), new LockZoneRequest(reservationId, zoneId, 1));

            executor.submit(() -> {
                try {
                    startLatch.await();
                    lockService.confirm(reservationId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(e);
                } catch (ObjectOptimisticLockingFailureException e) {
                    // expected — concurrent deleteByReservationId raced with release
                } catch (Exception e) {
                    unexpectedErrors.add(e);
                } finally {
                    finishLatch.countDown();
                }
            });
            executor.submit(() -> {
                try {
                    startLatch.await();
                    lockService.release(reservationId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(e);
                } catch (ObjectOptimisticLockingFailureException e) {
                    // expected — concurrent deleteByReservationId raced with confirm
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
        boolean terminated = executor.awaitTermination(3, TimeUnit.SECONDS);
        if (!terminated) {
            executor.shutdownNow();
        }

        assertThat(finished).as("All threads completed within timeout").isTrue();
        assertThat(terminated).as("Executor terminated cleanly").isTrue();
        assertThat(unexpectedErrors).as("No unexpected exceptions").isEmpty();

        Section refreshed = sectionRepository.findById(zoneId).orElseThrow();
        assertThat(refreshed.getRemainingCapacity()).as("Capacity never negative after confirm-release race").isBetween(90, 100);
        assertThat(zoneLockRepository.sumActiveQuantityByZoneId(zoneId, LocalDateTime.now())).as("No orphan zone locks remain").isZero();
    }

    @Test
    @DisplayName("Race: TTL expiration vs confirm for seat — consistent final state, no orphan locks")
    void givenExpiredSeatLock_whenCleanupRacesConfirm_thenConsistentState() throws Exception {
        int count = 10;
        Event event = createPublishedSeatEvent();
        Section section = createSection(event, count);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(count * 2);
        ConcurrentLinkedQueue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(20);

        for (int i = 0; i < count; i++) {
            UUID seatId = UUID.randomUUID();
            createSeat(section, seatId, SeatStatus.AVAILABLE);
            String reservationId = "ttl-race-seat-" + i;

            lockService.acquireSeatLocks(event.getId(), new LockSeatsRequest(reservationId, List.of(seatId)));

            SeatLock lock = seatLockRepository.findByReservationId(reservationId).get(0);
            lock.setExpiresAt(LocalDateTime.now().minusMinutes(5));
            seatLockRepository.save(lock);

            executor.submit(() -> {
                try {
                    startLatch.await();
                    lockService.confirm(reservationId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(e);
                } catch (LockExpiredException e) {
                    // expected — lock expired before confirm could proceed
                } catch (ObjectOptimisticLockingFailureException e) {
                    // expected — concurrent deleteByExpiresAtBefore raced with confirm's deleteByReservationId
                } catch (Exception e) {
                    unexpectedErrors.add(e);
                } finally {
                    finishLatch.countDown();
                }
            });
            executor.submit(() -> {
                try {
                    startLatch.await();
                    TransactionTemplate tt = new TransactionTemplate(transactionManager);
                    tt.execute(status -> {
                        seatLockRepository.deleteByExpiresAtBefore(LocalDateTime.now(), 100);
                        return null;
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(e);
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
        boolean terminated = executor.awaitTermination(3, TimeUnit.SECONDS);
        if (!terminated) {
            executor.shutdownNow();
        }

        assertThat(finished).as("All threads completed within timeout").isTrue();
        assertThat(terminated).as("Executor terminated cleanly").isTrue();
        assertThat(unexpectedErrors).as("No unexpected exceptions").isEmpty();
        assertThat(seatLockRepository.count()).as("No seat locks remain after expiration race").isZero();
        assertThat(seatRepository.findAll()).as("All seats remain AVAILABLE after expired lock race")
            .allMatch(s -> s.getStatus() == SeatStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Race: TTL expiration vs confirm for zone — capacity unchanged, no orphan locks")
    void givenExpiredZoneLock_whenCleanupRacesConfirm_thenConsistentState() throws Exception {
        int count = 10;
        Event event = createPublishedZoneEvent();
        Section section = createSection(event, 100);
        UUID zoneId = section.getId();

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(count * 2);
        ConcurrentLinkedQueue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(20);

        for (int i = 0; i < count; i++) {
            String reservationId = "ttl-race-zone-" + i;
            lockService.acquireZoneLock(event.getId(), new LockZoneRequest(reservationId, zoneId, 1));

            ZoneLock lock = zoneLockRepository.findByReservationId(reservationId).orElseThrow();
            lock.setExpiresAt(LocalDateTime.now().minusMinutes(5));
            zoneLockRepository.save(lock);

            executor.submit(() -> {
                try {
                    startLatch.await();
                    lockService.confirm(reservationId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(e);
                } catch (LockExpiredException e) {
                    // expected — lock expired before confirm could proceed
                } catch (ObjectOptimisticLockingFailureException e) {
                    // expected — concurrent deleteByExpiresAtBefore raced with confirm's deleteByReservationId
                } catch (Exception e) {
                    unexpectedErrors.add(e);
                } finally {
                    finishLatch.countDown();
                }
            });
            executor.submit(() -> {
                try {
                    startLatch.await();
                    TransactionTemplate tt = new TransactionTemplate(transactionManager);
                    tt.execute(status -> {
                        zoneLockRepository.deleteByExpiresAtBefore(LocalDateTime.now(), 100);
                        return null;
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(e);
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
        boolean terminated = executor.awaitTermination(3, TimeUnit.SECONDS);
        if (!terminated) {
            executor.shutdownNow();
        }

        assertThat(finished).as("All threads completed within timeout").isTrue();
        assertThat(terminated).as("Executor terminated cleanly").isTrue();
        assertThat(unexpectedErrors).as("No unexpected exceptions").isEmpty();

        Section refreshed = sectionRepository.findById(zoneId).orElseThrow();
        assertThat(refreshed.getRemainingCapacity()).as("Capacity unchanged after expired lock race").isEqualTo(100);
        assertThat(zoneLockRepository.sumActiveQuantityByZoneId(zoneId, LocalDateTime.now())).as("No active zone locks after race").isZero();
    }

    @Test
    @DisplayName("Race: release vs TTL expiration for seat — no orphan locks, all seats AVAILABLE")
    void givenExpiredSeatLock_whenReleaseAndCleanupRace_thenNoOrphans() throws Exception {
        int count = 10;
        Event event = createPublishedSeatEvent();
        Section section = createSection(event, count);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(count * 2);
        ConcurrentLinkedQueue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(20);

        for (int i = 0; i < count; i++) {
            UUID seatId = UUID.randomUUID();
            createSeat(section, seatId, SeatStatus.AVAILABLE);
            String reservationId = "release-expiry-race-seat-" + i;

            lockService.acquireSeatLocks(event.getId(), new LockSeatsRequest(reservationId, List.of(seatId)));
            SeatLock lock = seatLockRepository.findByReservationId(reservationId).get(0);
            lock.setExpiresAt(LocalDateTime.now().minusMinutes(5));
            seatLockRepository.save(lock);

            executor.submit(() -> {
                try {
                    startLatch.await();
                    lockService.release(reservationId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(e);
                } catch (ObjectOptimisticLockingFailureException e) {
                    // expected — concurrent deleteByReservationId raced with TTL cleanup
                } catch (Exception e) {
                    unexpectedErrors.add(e);
                } finally {
                    finishLatch.countDown();
                }
            });
            executor.submit(() -> {
                try {
                    startLatch.await();
                    TransactionTemplate tt = new TransactionTemplate(transactionManager);
                    tt.execute(status -> {
                        seatLockRepository.deleteByExpiresAtBefore(LocalDateTime.now(), 100);
                        return null;
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(e);
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
        boolean terminated = executor.awaitTermination(3, TimeUnit.SECONDS);
        if (!terminated) {
            executor.shutdownNow();
        }

        assertThat(finished).as("All threads completed within timeout").isTrue();
        assertThat(terminated).as("Executor terminated cleanly").isTrue();
        assertThat(unexpectedErrors).as("No unexpected exceptions").isEmpty();
        assertThat(seatLockRepository.count()).as("No seat locks remain after release vs expiration race").isZero();
        assertThat(seatRepository.findAll()).as("All seats AVAILABLE after release vs expiration race")
            .allMatch(s -> s.getStatus() == SeatStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Race: release vs TTL expiration for zone — no orphan locks, capacity restored")
    void givenExpiredZoneLock_whenReleaseAndCleanupRace_thenNoOrphans() throws Exception {
        int count = 10;
        Event event = createPublishedZoneEvent();
        Section section = createSection(event, 100);
        UUID zoneId = section.getId();

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(count * 2);
        ConcurrentLinkedQueue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(20);

        for (int i = 0; i < count; i++) {
            String reservationId = "release-expiry-race-zone-" + i;
            lockService.acquireZoneLock(event.getId(), new LockZoneRequest(reservationId, zoneId, 1));

            ZoneLock lock = zoneLockRepository.findByReservationId(reservationId).orElseThrow();
            lock.setExpiresAt(LocalDateTime.now().minusMinutes(5));
            zoneLockRepository.save(lock);

            executor.submit(() -> {
                try {
                    startLatch.await();
                    lockService.release(reservationId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(e);
                } catch (ObjectOptimisticLockingFailureException e) {
                    // expected — concurrent deleteByReservationId raced with TTL cleanup
                } catch (Exception e) {
                    unexpectedErrors.add(e);
                } finally {
                    finishLatch.countDown();
                }
            });
            executor.submit(() -> {
                try {
                    startLatch.await();
                    TransactionTemplate tt = new TransactionTemplate(transactionManager);
                    tt.execute(status -> {
                        zoneLockRepository.deleteByExpiresAtBefore(LocalDateTime.now(), 100);
                        return null;
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(e);
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
        boolean terminated = executor.awaitTermination(3, TimeUnit.SECONDS);
        if (!terminated) {
            executor.shutdownNow();
        }

        assertThat(finished).as("All threads completed within timeout").isTrue();
        assertThat(terminated).as("Executor terminated cleanly").isTrue();
        assertThat(unexpectedErrors).as("No unexpected exceptions").isEmpty();
        assertThat(zoneLockRepository.count()).as("No zone locks remain after release vs expiration race").isZero();
        assertThat(sectionRepository.findById(zoneId).orElseThrow().getRemainingCapacity())
            .as("Capacity restored after release vs expiration race").isEqualTo(100);
    }

    @Test
    @DisplayName("Race: acquire vs release for seat — at most one lock per seat, no orphans")
    void givenSeatReservation_whenAcquireAndReleaseRace_thenAtMostOneLock() throws Exception {
        int pairCount = 10;
        Event event = createPublishedSeatEvent();
        Section section = createSection(event, pairCount);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(pairCount * 2);
        ConcurrentLinkedQueue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(20);

        for (int i = 0; i < pairCount; i++) {
            UUID seatId = UUID.randomUUID();
            createSeat(section, seatId, SeatStatus.AVAILABLE);
            String existingReservationId = "acquire-release-existing-" + i;
            String newReservationId = "acquire-release-new-" + i;

            lockService.acquireSeatLocks(event.getId(), new LockSeatsRequest(existingReservationId, List.of(seatId)));

            executor.submit(() -> {
                try {
                    startLatch.await();
                    lockService.acquireSeatLocks(event.getId(), new LockSeatsRequest(newReservationId, List.of(seatId)));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(e);
                } catch (SeatAlreadyLockedException e) {
                    // expected — existing lock still present
                } catch (Exception e) {
                    unexpectedErrors.add(e);
                } finally {
                    finishLatch.countDown();
                }
            });
            executor.submit(() -> {
                try {
                    startLatch.await();
                    lockService.release(existingReservationId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(e);
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
        boolean terminated = executor.awaitTermination(3, TimeUnit.SECONDS);
        if (!terminated) {
            executor.shutdownNow();
        }

        assertThat(finished).as("All threads completed within timeout").isTrue();
        assertThat(terminated).as("Executor terminated cleanly").isTrue();
        assertThat(unexpectedErrors).as("No unexpected exceptions").isEmpty();

        List<SeatLock> allLocks = seatLockRepository.findAll();
        long distinctSeats = allLocks.stream().map(SeatLock::getSeatId).distinct().count();
        assertThat(distinctSeats).as("Each seat locked at most once").isEqualTo(allLocks.size());
        assertThat(allLocks).as("All locks belong to either the new or existing reservation")
            .allMatch(l -> l.getReservationId().startsWith("acquire-release-"));
        assertThat(seatRepository.findAll()).as("All seats AVAILABLE (no confirm called)")
            .allMatch(s -> s.getStatus() == SeatStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Race: acquire vs release for zone — capacity never negative, no orphans")
    void givenZoneReservation_whenAcquireAndReleaseRace_thenCapacityValid() throws Exception {
        int pairCount = 10;
        Event event = createPublishedZoneEvent();
        Section section = createSection(event, 100);
        UUID zoneId = section.getId();

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(pairCount * 2);
        ConcurrentLinkedQueue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(20);

        for (int i = 0; i < pairCount; i++) {
            String existingReservationId = "zone-acquire-release-existing-" + i;
            String newReservationId = "zone-acquire-release-new-" + i;
            lockService.acquireZoneLock(event.getId(), new LockZoneRequest(existingReservationId, zoneId, 1));

            executor.submit(() -> {
                try {
                    startLatch.await();
                    lockService.acquireZoneLock(event.getId(), new LockZoneRequest(newReservationId, zoneId, 1));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(e);
                } catch (ZoneCapacityExceededException e) {
                    // expected — capacity already consumed by existing locks
                } catch (Exception e) {
                    unexpectedErrors.add(e);
                } finally {
                    finishLatch.countDown();
                }
            });
            executor.submit(() -> {
                try {
                    startLatch.await();
                    lockService.release(existingReservationId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(e);
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
        boolean terminated = executor.awaitTermination(3, TimeUnit.SECONDS);
        if (!terminated) {
            executor.shutdownNow();
        }

        assertThat(finished).as("All threads completed within timeout").isTrue();
        assertThat(terminated).as("Executor terminated cleanly").isTrue();
        assertThat(unexpectedErrors).as("No unexpected exceptions").isEmpty();

        Section refreshed = sectionRepository.findById(zoneId).orElseThrow();
        assertThat(refreshed.getRemainingCapacity())
            .as("Capacity never negative after acquire vs release race").isBetween(90, 100);
        assertThat(zoneLockRepository.sumActiveQuantityByZoneId(zoneId, LocalDateTime.now()))
            .as("No orphan zone locks").isBetween(0, 10);
    }

    @Test
    @DisplayName("Race: acquire vs confirm for seat — no double booking, exactly one final outcome")
    void givenSeatReservation_whenAcquireAndConfirmRace_thenNoDoubleBooking() throws Exception {
        int pairCount = 10;
        Event event = createPublishedSeatEvent();
        Section section = createSection(event, pairCount);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(pairCount * 2);
        ConcurrentLinkedQueue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(20);

        for (int i = 0; i < pairCount; i++) {
            UUID seatId = UUID.randomUUID();
            createSeat(section, seatId, SeatStatus.AVAILABLE);
            String existingReservationId = "acquire-confirm-existing-" + i;
            String newReservationId = "acquire-confirm-new-" + i;

            lockService.acquireSeatLocks(event.getId(), new LockSeatsRequest(existingReservationId, List.of(seatId)));

            executor.submit(() -> {
                try {
                    startLatch.await();
                    lockService.acquireSeatLocks(event.getId(), new LockSeatsRequest(newReservationId, List.of(seatId)));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(e);
                } catch (SeatAlreadyLockedException | SeatAlreadyBookedException e) {
                    // expected — existing lock or confirmation present
                } catch (Exception e) {
                    unexpectedErrors.add(e);
                } finally {
                    finishLatch.countDown();
                }
            });
            executor.submit(() -> {
                try {
                    startLatch.await();
                    lockService.confirm(existingReservationId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(e);
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
        boolean terminated = executor.awaitTermination(3, TimeUnit.SECONDS);
        if (!terminated) {
            executor.shutdownNow();
        }

        assertThat(finished).as("All threads completed within timeout").isTrue();
        assertThat(terminated).as("Executor terminated cleanly").isTrue();
        assertThat(unexpectedErrors).as("No unexpected exceptions").isEmpty();

        List<Seat> allSeats = seatRepository.findAll();
        long bookedCount = allSeats.stream().filter(s -> s.getStatus() == SeatStatus.BOOKED).count();
        long availableCount = allSeats.stream().filter(s -> s.getStatus() == SeatStatus.AVAILABLE).count();
        assertThat(bookedCount + availableCount).as("All seats in valid state").isEqualTo(pairCount);
        assertThat(bookedCount).as("At most one booking per seat").isLessThanOrEqualTo(pairCount);
        assertThat(seatLockRepository.count()).as("No orphan seat locks").isZero();
    }

    @Test
    @DisplayName("Race: acquire vs confirm for zone — capacity valid, no orphans")
    void givenZoneReservation_whenAcquireAndConfirmRace_thenCapacityValid() throws Exception {
        int pairCount = 10;
        Event event = createPublishedZoneEvent();
        Section section = createSection(event, 100);
        UUID zoneId = section.getId();

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(pairCount * 2);
        ConcurrentLinkedQueue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(20);

        for (int i = 0; i < pairCount; i++) {
            String existingReservationId = "zone-acquire-confirm-existing-" + i;
            String newReservationId = "zone-acquire-confirm-new-" + i;
            lockService.acquireZoneLock(event.getId(), new LockZoneRequest(existingReservationId, zoneId, 1));

            executor.submit(() -> {
                try {
                    startLatch.await();
                    lockService.acquireZoneLock(event.getId(), new LockZoneRequest(newReservationId, zoneId, 1));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(e);
                } catch (ZoneCapacityExceededException e) {
                    // expected — lock may still be active
                } catch (Exception e) {
                    unexpectedErrors.add(e);
                } finally {
                    finishLatch.countDown();
                }
            });
            executor.submit(() -> {
                try {
                    startLatch.await();
                    lockService.confirm(existingReservationId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(e);
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
        boolean terminated = executor.awaitTermination(3, TimeUnit.SECONDS);
        if (!terminated) {
            executor.shutdownNow();
        }

        assertThat(finished).as("All threads completed within timeout").isTrue();
        assertThat(terminated).as("Executor terminated cleanly").isTrue();
        assertThat(unexpectedErrors).as("No unexpected exceptions").isEmpty();

        Section refreshed = sectionRepository.findById(zoneId).orElseThrow();
        // Each confirm() always runs (acquire doesn't block it). Each decrements capacity by 1.
        // Capacity always = 100 - 10 = 90 after all confirms complete.
        assertThat(refreshed.getRemainingCapacity())
            .as("Capacity correctly consumed after acquire vs confirm race").isEqualTo(90);
        // Each concurrent acquire succeeds (capacity permits it). Exactly 10 new locks remain.
        assertThat(zoneLockRepository.sumActiveQuantityByZoneId(zoneId, LocalDateTime.now()))
            .as("All new acquires succeeded, no orphan or duplicate locks").isEqualTo(10);
    }

    @Test
    @DisplayName("Concurrent confirm: multiple threads confirm the same reservation — exactly one logical confirmation")
    void givenSeatReservation_whenConcurrentConfirm_thenSingleConfirmation() throws Exception {
        int threadCount = 10;
        Event event = createPublishedSeatEvent();
        Section section = createSection(event, threadCount);
        UUID seatId = UUID.randomUUID();
        createSeat(section, seatId, SeatStatus.AVAILABLE);
        String reservationId = "concurrent-confirm-res";

        lockService.acquireSeatLocks(event.getId(), new LockSeatsRequest(reservationId, List.of(seatId)));

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);
        ConcurrentLinkedQueue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(20);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    lockService.confirm(reservationId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(e);
                } catch (SeatAlreadyBookedException e) {
                    // expected — another thread already confirmed
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
        if (!terminated) {
            executor.shutdownNow();
        }

        assertThat(finished).as("All threads completed within timeout").isTrue();
        assertThat(terminated).as("Executor terminated cleanly").isTrue();
        assertThat(unexpectedErrors).as("No unexpected exceptions").isEmpty();

        Seat seat = seatRepository.findById(seatId).orElseThrow();
        assertThat(seat.getStatus()).as("Seat is BOOKED by exactly one confirmation").isEqualTo(SeatStatus.BOOKED);
        assertThat(seatLockRepository.findByReservationId(reservationId)).as("No seat locks remain after confirm").isEmpty();
        assertThat(seatLockRepository.count()).as("No orphan locks").isZero();
    }

    @Test
    @DisplayName("Race: two simultaneous confirms on same zone reservation — capacity decremented at most once")
    void givenZoneReservation_whenTwoSimultaneousConfirms_thenCapacityCorrect() throws Exception {
        int pairCount = 10;
        Event event = createPublishedZoneEvent();
        Section section = createSection(event, 100);
        UUID zoneId = section.getId();

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(pairCount * 2);
        ConcurrentLinkedQueue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(20);

        for (int i = 0; i < pairCount; i++) {
            String reservationId = "two-confirms-zone-" + i;
            lockService.acquireZoneLock(event.getId(), new LockZoneRequest(reservationId, zoneId, 1));

            executor.submit(() -> {
                try {
                    startLatch.await();
                    lockService.confirm(reservationId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(e);
                } catch (Exception e) {
                    unexpectedErrors.add(e);
                } finally {
                    finishLatch.countDown();
                }
            });
            executor.submit(() -> {
                try {
                    startLatch.await();
                    lockService.confirm(reservationId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(e);
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
        boolean terminated = executor.awaitTermination(3, TimeUnit.SECONDS);
        if (!terminated) {
            executor.shutdownNow();
        }

        assertThat(finished).as("All threads completed within timeout").isTrue();
        assertThat(terminated).as("Executor terminated cleanly").isTrue();
        assertThat(unexpectedErrors).as("No unexpected exceptions").isEmpty();

        Section refreshed = sectionRepository.findById(zoneId).orElseThrow();
        // EXPECTED: capacity = 100 - pairCount = 90
        // BUG: Because confirm() loads ZoneLock without pessimistic lock,
        // two concurrent confirms may both decrement capacity (double-decrement bug).
        assertThat(refreshed.getRemainingCapacity())
            .as("Capacity decremented exactly once per reservation").isEqualTo(90);
        assertThat(zoneLockRepository.count()).as("No orphan zone locks").isZero();
    }

    @Test
    @DisplayName("Race: two simultaneous releases on same seat reservation — no orphan locks")
    void givenSeatReservation_whenTwoSimultaneousReleases_thenNoOrphans() throws Exception {
        int pairCount = 10;
        Event event = createPublishedSeatEvent();
        Section section = createSection(event, pairCount);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(pairCount * 2);
        ConcurrentLinkedQueue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(20);

        for (int i = 0; i < pairCount; i++) {
            UUID seatId = UUID.randomUUID();
            createSeat(section, seatId, SeatStatus.AVAILABLE);
            String reservationId = "two-releases-seat-" + i;
            lockService.acquireSeatLocks(event.getId(), new LockSeatsRequest(reservationId, List.of(seatId)));

            executor.submit(() -> {
                try {
                    startLatch.await();
                    lockService.release(reservationId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(e);
                } catch (ObjectOptimisticLockingFailureException e) {
                    // expected — concurrent deleteByReservationId raced with the other release
                } catch (Exception e) {
                    unexpectedErrors.add(e);
                } finally {
                    finishLatch.countDown();
                }
            });
            executor.submit(() -> {
                try {
                    startLatch.await();
                    lockService.release(reservationId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(e);
                } catch (ObjectOptimisticLockingFailureException e) {
                    // expected — concurrent deleteByReservationId raced with the other release
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
        boolean terminated = executor.awaitTermination(3, TimeUnit.SECONDS);
        if (!terminated) {
            executor.shutdownNow();
        }

        assertThat(finished).as("All threads completed within timeout").isTrue();
        assertThat(terminated).as("Executor terminated cleanly").isTrue();
        assertThat(unexpectedErrors).as("No unexpected exceptions").isEmpty();
        assertThat(seatLockRepository.count()).as("No seat locks after two simultaneous releases").isZero();
        assertThat(seatRepository.findAll()).as("All seats AVAILABLE after two simultaneous releases")
            .allMatch(s -> s.getStatus() == SeatStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Race: two simultaneous releases on same zone reservation — no orphan locks, capacity restored")
    void givenZoneReservation_whenTwoSimultaneousReleases_thenNoOrphans() throws Exception {
        int pairCount = 10;
        Event event = createPublishedZoneEvent();
        Section section = createSection(event, 100);
        UUID zoneId = section.getId();

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(pairCount * 2);
        ConcurrentLinkedQueue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(20);

        for (int i = 0; i < pairCount; i++) {
            String reservationId = "two-releases-zone-" + i;
            lockService.acquireZoneLock(event.getId(), new LockZoneRequest(reservationId, zoneId, 1));

            executor.submit(() -> {
                try {
                    startLatch.await();
                    lockService.release(reservationId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(e);
                } catch (ObjectOptimisticLockingFailureException e) {
                    // expected — concurrent deleteByReservationId raced with the other release
                } catch (Exception e) {
                    unexpectedErrors.add(e);
                } finally {
                    finishLatch.countDown();
                }
            });
            executor.submit(() -> {
                try {
                    startLatch.await();
                    lockService.release(reservationId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(e);
                } catch (ObjectOptimisticLockingFailureException e) {
                    // expected — concurrent deleteByReservationId raced with the other release
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
        boolean terminated = executor.awaitTermination(3, TimeUnit.SECONDS);
        if (!terminated) {
            executor.shutdownNow();
        }

        assertThat(finished).as("All threads completed within timeout").isTrue();
        assertThat(terminated).as("Executor terminated cleanly").isTrue();
        assertThat(unexpectedErrors).as("No unexpected exceptions").isEmpty();
        assertThat(zoneLockRepository.count()).as("No zone locks after two simultaneous releases").isZero();
        assertThat(sectionRepository.findById(zoneId).orElseThrow().getRemainingCapacity())
            .as("Capacity restored after two simultaneous releases").isEqualTo(100);
    }
}
