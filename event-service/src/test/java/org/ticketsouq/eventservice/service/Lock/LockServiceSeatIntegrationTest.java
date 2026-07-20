package org.ticketsouq.eventservice.service.Lock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.ticketsouq.eventservice.model.*;
import org.ticketsouq.eventservice.model.enums.SeatStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.ticketsouq.sharedmodule.EventService.exception.*;
import org.ticketsouq.sharedmodule.EventService.dto.LockSeatsRequest;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LockServiceSeatIntegrationTest extends LockServiceIntegrationTestBase {

    @Test
    @DisplayName("Atomic reservation: overlapping multi-seat requests — exactly one succeeds atomically")
    void givenOverlappingMultiSeatRequests_whenConcurrent_thenAtomicOutcome() throws Exception {
        Event event = createPublishedSeatEvent();
        Section section = createSection(event, 10);
        UUID seatA = UUID.randomUUID();
        UUID seatB = UUID.randomUUID();
        UUID seatC = UUID.randomUUID();
        createSeat(section, seatA, SeatStatus.AVAILABLE);
        createSeat(section, seatB, SeatStatus.AVAILABLE);
        createSeat(section, seatC, SeatStatus.AVAILABLE);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(2);
        ConcurrentLinkedQueue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();
        AtomicInteger requestASucceeded = new AtomicInteger(0);
        AtomicInteger requestBSucceeded = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.submit(() -> {
            try {
                startLatch.await();
                lockService.acquireSeatLocks(event.getId(), new LockSeatsRequest("multi-seat-A", List.of(seatA, seatB, seatC)));
                requestASucceeded.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                unexpectedErrors.add(e);
            } catch (SeatAlreadyLockedException | SeatAlreadyBookedException e) {
                // expected — one request must fail
            } catch (Exception e) {
                unexpectedErrors.add(e);
            } finally {
                finishLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                lockService.acquireSeatLocks(event.getId(), new LockSeatsRequest("multi-seat-B", List.of(seatB, seatC)));
                requestBSucceeded.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                unexpectedErrors.add(e);
            } catch (SeatAlreadyLockedException | SeatAlreadyBookedException e) {
                // expected — one request must fail
            } catch (Exception e) {
                unexpectedErrors.add(e);
            } finally {
                finishLatch.countDown();
            }
        });

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
        assertThat(requestASucceeded.get() + requestBSucceeded.get()).as("Exactly one multi-seat reservation succeeds").isEqualTo(1);

        String winningReservationId = requestASucceeded.get() == 1 ? "multi-seat-A" : "multi-seat-B";
        List<SeatLock> allLocks = seatLockRepository.findAll();
        assertThat(allLocks).as("All locks belong to the winning reservation")
            .allMatch(l -> l.getReservationId().equals(winningReservationId));
        int expectedMinLocks = winningReservationId.equals("multi-seat-A") ? 3 : 2;
        assertThat(allLocks).as("Winner has all its requested locks").hasSize(expectedMinLocks);
        assertThat(seatRepository.findAll()).as("All seats still AVAILABLE (no confirm was called)")
            .allMatch(s -> s.getStatus() == SeatStatus.AVAILABLE);
    }

    @Test
    @DisplayName("High contention: mixed-size overlapping reservations — no partial reservations, no duplicate locks")
    void givenMixedSizeOverlappingReservations_whenConcurrent_thenNoDuplicatesOrPartials() throws Exception {
        int totalSeats = 10;
        Event event = createPublishedSeatEvent();
        Section section = createSection(event, totalSeats);
        List<UUID> seatIds = new ArrayList<>();
        for (int i = 0; i < totalSeats; i++) {
            UUID sid = UUID.randomUUID();
            seatIds.add(sid);
            createSeat(section, sid, SeatStatus.AVAILABLE);
        }

        record Req(String id, List<UUID> seats, int expectedSize) {}
        List<Req> requests = List.of(
            new Req("mix-0", List.of(seatIds.get(0), seatIds.get(1), seatIds.get(2)), 3),
            new Req("mix-1", List.of(seatIds.get(1), seatIds.get(2)), 2),
            new Req("mix-2", List.of(seatIds.get(3), seatIds.get(4), seatIds.get(5), seatIds.get(6)), 4),
            new Req("mix-3", List.of(seatIds.get(5), seatIds.get(6), seatIds.get(7)), 3),
            new Req("mix-4", List.of(seatIds.get(8), seatIds.get(9)), 2),
            new Req("mix-5", List.of(seatIds.get(0), seatIds.get(9)), 2)
        );
        int requestCount = requests.size();

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(requestCount);
        ConcurrentLinkedQueue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<String> succeededReservations = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(20);

        for (Req req : requests) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    lockService.acquireSeatLocks(event.getId(), new LockSeatsRequest(req.id, req.seats));
                    succeededReservations.add(req.id);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(e);
                } catch (SeatAlreadyLockedException | SeatAlreadyBookedException e) {
                    // expected — overlapping requests may conflict
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

        List<SeatLock> allLocks = seatLockRepository.findAll();
        long distinctLockedSeats = allLocks.stream().map(SeatLock::getSeatId).distinct().count();
        assertThat(distinctLockedSeats).as("No seat locked by multiple reservations").isEqualTo(allLocks.size());
        assertThat(allLocks).as("Each successful reservation has exactly the right number of locks").allMatch(lock -> {
            Req matchingReq = requests.stream().filter(r -> r.id.equals(lock.getReservationId())).findFirst().orElse(null);
            if (matchingReq == null) return false;
            long countForThisReservation = allLocks.stream().filter(l -> l.getReservationId().equals(lock.getReservationId())).count();
            return countForThisReservation == matchingReq.expectedSize;
        });
        assertThat(seatRepository.findAll()).as("All seats still AVAILABLE (no confirm called)")
            .allMatch(s -> s.getStatus() == SeatStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Transaction rollback: confirm after seat manually set to BOOKED — rolled back cleanly, lock preserved")
    void givenSeatManuallyBooked_whenConfirm_thenTransactionRollsBack() {
        Event event = createPublishedSeatEvent();
        Section section = createSection(event, 10);
        UUID seatId = UUID.randomUUID();
        createSeat(section, seatId, SeatStatus.AVAILABLE);
        String reservationId = "rollback-test-res";

        lockService.acquireSeatLocks(event.getId(), new LockSeatsRequest(reservationId, List.of(seatId)));
        assertThat(seatLockRepository.findByReservationId(reservationId)).isNotEmpty();

        Seat seat = seatRepository.findById(seatId).orElseThrow();
        seat.setStatus(SeatStatus.BOOKED);
        seatRepository.save(seat);

        org.junit.jupiter.api.Assertions.assertThrows(SeatAlreadyBookedException.class,
            () -> lockService.confirm(reservationId));

        assertThat(seatLockRepository.findByReservationId(reservationId))
            .as("Seat lock still exists after transaction rollback").isNotEmpty();
        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus())
            .as("Seat remains BOOKED (set directly, confirm rolled back)").isEqualTo(SeatStatus.BOOKED);
        assertThat(seatLockRepository.count()).as("Only the original seat lock exists").isEqualTo(1);
        assertThat(sectionRepository.findById(section.getId()).orElseThrow().getRemainingCapacity())
            .as("Section capacity unchanged").isEqualTo(10);
    }

    @Test
    @DisplayName("Consistency: 200-seat bulk lifecycle — acquire 150, confirm 100, release 25, re-acquire 25, confirm 15, release 5. Verify all invariants.")
    void givenBulkSeatLifecycle_whenManyOperations_thenAllInvariantsHold() {
        int totalSeats = 200;
        Event event = createPublishedSeatEvent();
        Section section = createSection(event, totalSeats);
        List<UUID> seatIds = new ArrayList<>();
        for (int i = 0; i < totalSeats; i++) {
            UUID sid = UUID.randomUUID();
            seatIds.add(sid);
            createSeat(section, sid, SeatStatus.AVAILABLE);
        }

        // Phase 1: Acquire 150 of 200 seats
        for (int i = 0; i < 150; i++) {
            lockService.acquireSeatLocks(event.getId(), new LockSeatsRequest("cs-seat-A-" + i, List.of(seatIds.get(i))));
        }
        assertThat(seatLockRepository.count()).as("150 locks after phase 1").isEqualTo(150);

        // Phase 2: Confirm 100 of the 150
        for (int i = 0; i < 100; i++) {
            lockService.confirm("cs-seat-A-" + i);
        }
        assertThat(seatLockRepository.count()).as("50 locks after phase 2 (150 - 100 confirmed)").isEqualTo(50);
        assertThat(seatRepository.findAll().stream().filter(s -> s.getStatus() == SeatStatus.BOOKED).count())
            .as("100 seats booked after phase 2").isEqualTo(100);

        // Phase 3: Release 25 of the remaining 50 locked seats
        for (int i = 100; i < 125; i++) {
            lockService.release("cs-seat-A-" + i);
        }
        assertThat(seatLockRepository.count()).as("25 locks after phase 3 (50 - 25 released)").isEqualTo(25);

        // Phase 4: Re-acquire 25 released seats with new reservation IDs
        for (int i = 0; i < 25; i++) {
            lockService.acquireSeatLocks(event.getId(), new LockSeatsRequest("cs-seat-B-" + i, List.of(seatIds.get(100 + i))));
        }
        assertThat(seatLockRepository.count()).as("50 locks after phase 4 (25 old + 25 re-acquired)").isEqualTo(50);

        // Phase 5: Confirm 15 of the newly acquired seats
        for (int i = 0; i < 15; i++) {
            lockService.confirm("cs-seat-B-" + i);
        }
        assertThat(seatLockRepository.count()).as("35 locks after phase 5 (25 old + 10 new)").isEqualTo(35);
        assertThat(seatRepository.findAll().stream().filter(s -> s.getStatus() == SeatStatus.BOOKED).count())
            .as("115 seats booked after phase 5 (100 + 15)").isEqualTo(115);

        // Phase 6: Release 5 of the remaining newly acquired seats
        for (int i = 15; i < 20; i++) {
            lockService.release("cs-seat-B-" + i);
        }
        assertThat(seatLockRepository.count()).as("30 locks after phase 6 (25 old + 5 new)").isEqualTo(30);

        // ── Final invariant verification ──────────────────────────────────────────

        // Invariant 1: no seats were created or destroyed
        assertThat(seatRepository.count()).as("Total seats unchanged").isEqualTo(totalSeats);

        // Invariant 2: every seat is either AVAILABLE or BOOKED
        List<Seat> allSeats = seatRepository.findAll();
        long availableCount = allSeats.stream().filter(s -> s.getStatus() == SeatStatus.AVAILABLE).count();
        long bookedCount = allSeats.stream().filter(s -> s.getStatus() == SeatStatus.BOOKED).count();
        assertThat(availableCount + bookedCount).as("All seats have valid status").isEqualTo(totalSeats);
        assertThat(bookedCount).as("Total booked = 115").isEqualTo(115);

        // Invariant 3: no duplicate seat IDs in seat_locks (DB unique constraint enforces this)
        List<SeatLock> allLocks = seatLockRepository.findAll();
        long distinctLockSeatIds = allLocks.stream().map(SeatLock::getSeatId).distinct().count();
        assertThat(distinctLockSeatIds).as("No duplicate seatId in locks").isEqualTo(allLocks.size());

        // Invariant 4: no seat is simultaneously BOOKED and locked
        Set<UUID> lockedSeatIds = allLocks.stream().map(SeatLock::getSeatId).collect(Collectors.toSet());
        long bookedAndLocked = allSeats.stream()
            .filter(s -> s.getStatus() == SeatStatus.BOOKED && lockedSeatIds.contains(s.getId()))
            .count();
        assertThat(bookedAndLocked).as("No seat is both BOOKED and locked").isZero();

        // Invariant 5: every lock's seatId references a valid seat
        Set<UUID> allSeatIds = allSeats.stream().map(Seat::getId).collect(Collectors.toSet());
        long orphanLocks = allLocks.stream().filter(l -> !allSeatIds.contains(l.getSeatId())).count();
        assertThat(orphanLocks).as("No orphan lock rows (every lock references a valid seat)").isZero();

        // Invariant 6: remainingCapacity was NOT modified by seat operations (seat-model events don't touch section capacity)
        assertThat(sectionRepository.findById(section.getId()).orElseThrow().getRemainingCapacity())
            .as("Section remainingCapacity unchanged in seat-booking model").isEqualTo(totalSeats);

        // Invariant 7: exactly 200 - 115 - 30 = 55 seats were never acquired
        long neverAcquired = availableCount - allLocks.size();
        assertThat(neverAcquired).as("55 seats never acquired (200 - 115 BOOKED - 30 locked)").isEqualTo(55);
    }

    @Test
    @DisplayName("Exception: acquireSeatLocks on already BOOKED seat throws SeatAlreadyBookedException")
    void givenBookedSeat_whenAcquireSeatLock_thenThrowsSeatAlreadyBooked() {
        Event event = createPublishedSeatEvent();
        Section section = createSection(event, 10);
        UUID seatId = UUID.randomUUID();
        createSeat(section, seatId, SeatStatus.AVAILABLE);

        lockService.acquireSeatLocks(event.getId(), new LockSeatsRequest("book-seat", List.of(seatId)));
        lockService.confirm("book-seat");

        assertThrows(SeatAlreadyBookedException.class,
            () -> lockService.acquireSeatLocks(event.getId(), new LockSeatsRequest("reacquire-booked", List.of(seatId))));

        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus())
            .as("Seat remains BOOKED").isEqualTo(SeatStatus.BOOKED);
        assertThat(seatLockRepository.count()).as("No new locks created").isEqualTo(0);
    }

    @Test
    @DisplayName("Exception: multi-seat acquire with one seat already BOOKED throws SeatAlreadyBookedException, no partial locks")
    void givenMultiSeatRequest_whenOneSeatIsBooked_thenNoPartialLocks() {
        Event event = createPublishedSeatEvent();
        Section section = createSection(event, 10);
        UUID seatA = UUID.randomUUID();
        UUID seatB = UUID.randomUUID();
        createSeat(section, seatA, SeatStatus.AVAILABLE);
        createSeat(section, seatB, SeatStatus.AVAILABLE);

        lockService.acquireSeatLocks(event.getId(), new LockSeatsRequest("book-seat-A", List.of(seatA)));
        lockService.confirm("book-seat-A");

        SeatAlreadyBookedException ex = assertThrows(SeatAlreadyBookedException.class,
            () -> lockService.acquireSeatLocks(event.getId(), new LockSeatsRequest("partial-booked", List.of(seatA, seatB))));

//        assertThat(ex.getConflictingSeats()).as("Exception lists the booked seat").containsExactly(seatA);
        assertThat(seatRepository.findById(seatB).orElseThrow().getStatus())
            .as("Seat B remains AVAILABLE — no partial lock creation").isEqualTo(SeatStatus.AVAILABLE);
        assertThat(seatLockRepository.count()).as("No locks created for either seat").isZero();
    }

    @Test
    @DisplayName("Rollback: confirmSeats deletes expired lock then throws — delete is rolled back, seat AVAILABLE")
    void givenExpiredSeatLock_whenConfirmThrowsLockExpired_thenDeleteRolledBack() {
        Event event = createPublishedSeatEvent();
        Section section = createSection(event, 10);
        UUID seatId = UUID.randomUUID();
        createSeat(section, seatId, SeatStatus.AVAILABLE);
        String reservationId = "rollback-expired-seat";

        lockService.acquireSeatLocks(event.getId(), new LockSeatsRequest(reservationId, List.of(seatId)));
        SeatLock lock = seatLockRepository.findByReservationId(reservationId).get(0);
        lock.setExpiresAt(LocalDateTime.now().minusMinutes(5));
        seatLockRepository.save(lock);

        assertThrows(LockExpiredException.class, () -> lockService.confirm(reservationId));

        List<SeatLock> remaining = seatLockRepository.findByReservationId(reservationId);
        assertThat(remaining).as("Expired lock still exists — transaction rolled back the delete").isNotEmpty();
        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus())
            .as("Seat still AVAILABLE — confirm did not book").isEqualTo(SeatStatus.AVAILABLE);
        assertThat(seatLockRepository.count()).as("No orphan locks").isEqualTo(1);
    }

    @Test
    @DisplayName("Rollback: outer transaction throws after acquireSeatLocks — lock creation rolled back")
    void givenSeatAcquisition_whenOuterTxRollsBack_thenLockNotPersisted() {
        Event event = createPublishedSeatEvent();
        Section section = createSection(event, 10);
        UUID seatId = UUID.randomUUID();
        createSeat(section, seatId, SeatStatus.AVAILABLE);
        String reservationId = "rollback-acquire-seat";

        TransactionTemplate tt = new TransactionTemplate(transactionManager);
        assertThrows(RuntimeException.class, () -> tt.execute(status -> {
            lockService.acquireSeatLocks(event.getId(), new LockSeatsRequest(reservationId, List.of(seatId)));
            SeatLock saved = seatLockRepository.findByReservationId(reservationId).get(0);
            assertThat(saved).as("Lock visible within the transaction before rollback").isNotNull();
            throw new RuntimeException("simulated failure after acquire");
        }));

        assertThat(seatLockRepository.findByReservationId(reservationId))
            .as("No seat lock persisted after transaction rollback").isEmpty();
        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus())
            .as("Seat status unchanged after rollback").isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Rollback: outer transaction throws after confirm(seat) — booking and lock deletion rolled back")
    void givenSeatConfirm_whenOuterTxRollsBack_thenBookingAndDeleteReversed() {
        Event event = createPublishedSeatEvent();
        Section section = createSection(event, 10);
        UUID seatId = UUID.randomUUID();
        createSeat(section, seatId, SeatStatus.AVAILABLE);
        String reservationId = "rollback-confirm-seat";

        lockService.acquireSeatLocks(event.getId(), new LockSeatsRequest(reservationId, List.of(seatId)));

        TransactionTemplate tt = new TransactionTemplate(transactionManager);
        assertThrows(RuntimeException.class, () -> tt.execute(status -> {
            lockService.confirm(reservationId);
            Seat booked = seatRepository.findById(seatId).orElseThrow();
            assertThat(booked.getStatus()).as("Seat BOOKED within transaction before rollback").isEqualTo(SeatStatus.BOOKED);
            assertThat(seatLockRepository.findByReservationId(reservationId))
                .as("Lock deleted within transaction before rollback").isEmpty();
            throw new RuntimeException("simulated failure after confirm");
        }));

        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus())
            .as("Seat AVAILABLE after rollback — booking reversed").isEqualTo(SeatStatus.AVAILABLE);
        assertThat(seatLockRepository.findByReservationId(reservationId))
            .as("Lock restored after rollback — delete reversed").isNotEmpty();
        assertThat(seatLockRepository.count()).as("No orphan locks").isEqualTo(1);
    }

    @Test
    @DisplayName("Rollback: outer transaction throws after release(seat) — lock deletion rolled back")
    void givenSeatRelease_whenOuterTxRollsBack_thenDeleteReversed() {
        Event event = createPublishedSeatEvent();
        Section section = createSection(event, 10);
        UUID seatId = UUID.randomUUID();
        createSeat(section, seatId, SeatStatus.AVAILABLE);
        String reservationId = "rollback-release-seat";

        lockService.acquireSeatLocks(event.getId(), new LockSeatsRequest(reservationId, List.of(seatId)));

        TransactionTemplate tt = new TransactionTemplate(transactionManager);
        assertThrows(RuntimeException.class, () -> tt.execute(status -> {
            lockService.release(reservationId);
            assertThat(seatLockRepository.findByReservationId(reservationId))
                .as("Lock deleted within transaction before rollback").isEmpty();
            throw new RuntimeException("simulated failure after release");
        }));

        assertThat(seatLockRepository.findByReservationId(reservationId))
            .as("Lock restored after rollback — delete reversed").isNotEmpty();
        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus())
            .as("Seat still AVAILABLE after rollback").isEqualTo(SeatStatus.AVAILABLE);
        assertThat(seatLockRepository.count()).as("No orphan locks").isEqualTo(1);
    }
}
