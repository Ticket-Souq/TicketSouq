package org.ticketsouq.eventservice.service.Lock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.ticketsouq.eventservice.model.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.ticketsouq.sharedmodule.EventService.dto.LockZoneRequest;
import org.ticketsouq.sharedmodule.EventService.exception.LockExpiredException;
import org.ticketsouq.sharedmodule.EventService.exception.ZoneCapacityExceededException;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LockServiceZoneIntegrationTest extends LockServiceIntegrationTestBase {

    @Test
    @DisplayName("Zone acquire: quantity > 1 correctly reserves and consumes capacity")
    void givenZoneReservation_whenQuantityGtOne_thenCapacityReservedAndConsumed() {
        Event event = createPublishedZoneEvent();
        Section section = createSection(event, 100);
        String resId = "zone-qty-3";

        lockService.acquireZoneLock(event.getId(), new LockZoneRequest(resId, section.getId(), 3));

        int active = zoneLockRepository.sumActiveQuantityByZoneId(section.getId(), LocalDateTime.now());
        assertThat(active).as("3 quantity reserved by acquire").isEqualTo(3);
        assertThat(sectionRepository.findById(section.getId()).orElseThrow().getRemainingCapacity())
            .as("remainingCapacity unchanged after acquire (only confirm decrements)").isEqualTo(100);

        lockService.confirm(resId);

        assertThat(sectionRepository.findById(section.getId()).orElseThrow().getRemainingCapacity())
            .as("Capacity reduced by 3 after confirm").isEqualTo(97);
        assertThat(zoneLockRepository.sumActiveQuantityByZoneId(section.getId(), LocalDateTime.now()))
            .as("No active locks after confirm").isZero();
    }

    @Test
    @DisplayName("Zone acquire: quantity > available capacity throws ZoneCapacityExceededException")
    void givenZoneReservation_whenQuantityExceedsCapacity_thenThrows() {
        Event event = createPublishedZoneEvent();
        Section section = createSection(event, 5);

        ZoneCapacityExceededException ex = assertThrows(ZoneCapacityExceededException.class,
            () -> lockService.acquireZoneLock(event.getId(), new LockZoneRequest("zone-qty-exceed", section.getId(), 10)));

        assertThat(ex.getAvailable()).as("Available capacity reported").isEqualTo(5);
        assertThat(zoneLockRepository.count()).as("No lock created").isZero();
    }

    @Test
    @DisplayName("Consistency: 100-capacity zone bulk lifecycle — acquire 80, confirm 50, release 10, acquire 15, confirm 10, acquire more. Verify all invariants.")
    void givenBulkZoneLifecycle_whenManyOperations_thenAllInvariantsHold() {
        int capacity = 100;
        Event event = createPublishedZoneEvent();
        Section section = createSection(event, capacity);
        UUID zoneId = section.getId();

        // Phase 1: Acquire 80 zone locks (quantity 1 each)
        for (int i = 0; i < 80; i++) {
            lockService.acquireZoneLock(event.getId(), new LockZoneRequest("cs-zone-A-" + i, zoneId, 1));
        }
        assertThat(zoneLockRepository.sumActiveQuantityByZoneId(zoneId, LocalDateTime.now()))
            .as("80 active lock quantity after phase 1").isEqualTo(80);

        // Phase 2: Confirm 50
        for (int i = 0; i < 50; i++) {
            lockService.confirm("cs-zone-A-" + i);
        }
        assertThat(sectionRepository.findById(zoneId).orElseThrow().getRemainingCapacity())
            .as("remainingCapacity = 50 after phase 2 (100 - 50 confirmed)").isEqualTo(50);
        assertThat(zoneLockRepository.sumActiveQuantityByZoneId(zoneId, LocalDateTime.now()))
            .as("30 active locks after phase 2 (80 - 50 confirmed)").isEqualTo(30);

        // Phase 3: Release 10 of the remaining 30
        for (int i = 50; i < 60; i++) {
            lockService.release("cs-zone-A-" + i);
        }
        assertThat(zoneLockRepository.sumActiveQuantityByZoneId(zoneId, LocalDateTime.now()))
            .as("20 active locks after phase 3 (30 - 10 released)").isEqualTo(20);

        // Phase 4: Acquire 15 more (available capacity = 50 - 20 = 30, enough for 15)
        for (int i = 0; i < 15; i++) {
            lockService.acquireZoneLock(event.getId(), new LockZoneRequest("cs-zone-B-" + i, zoneId, 1));
        }
        assertThat(zoneLockRepository.sumActiveQuantityByZoneId(zoneId, LocalDateTime.now()))
            .as("35 active locks after phase 4 (20 + 15)").isEqualTo(35);

        // Phase 5: Confirm 10 of the new ones
        for (int i = 0; i < 10; i++) {
            lockService.confirm("cs-zone-B-" + i);
        }
        assertThat(sectionRepository.findById(zoneId).orElseThrow().getRemainingCapacity())
            .as("remainingCapacity = 40 after phase 5 (50 - 10 confirmed)").isEqualTo(40);
        assertThat(zoneLockRepository.sumActiveQuantityByZoneId(zoneId, LocalDateTime.now()))
            .as("25 active locks after phase 5 (35 - 10 confirmed)").isEqualTo(25);

        // Phase 6: Acquire 10 more (available = 40 - 25 = 15, enough for 10)
        for (int i = 0; i < 10; i++) {
            lockService.acquireZoneLock(event.getId(), new LockZoneRequest("cs-zone-C-" + i, zoneId, 1));
        }
        assertThat(zoneLockRepository.sumActiveQuantityByZoneId(zoneId, LocalDateTime.now()))
            .as("35 active locks after phase 6 (25 + 10)").isEqualTo(35);

        // ── Final invariant verification ──────────────────────────────────────────

        Section refreshed = sectionRepository.findById(zoneId).orElseThrow();

        // Invariant 1: remainingCapacity never negative
        assertThat(refreshed.getRemainingCapacity()).as("remainingCapacity >= 0").isNotNegative();

        // Invariant 2: remainingCapacity never exceeds initial capacity
        assertThat(refreshed.getRemainingCapacity()).as("remainingCapacity <= initial capacity").isLessThanOrEqualTo(capacity);

        // Invariant 3: remainingCapacity + confirmedBookings == originalCapacity
        // Confirmed bookings = 50 + 10 = 60. remainingCapacity = 100 - 60 = 40.
        assertThat(refreshed.getRemainingCapacity()).as("remainingCapacity = 40").isEqualTo(40);

        // Invariant 4: active lock quantity sum matches DB count
        int activeQty = zoneLockRepository.sumActiveQuantityByZoneId(zoneId, LocalDateTime.now());
        assertThat(activeQty).as("active lock quantity = 35 (20 old + 10 C + 5 remaining from B after confirming 10 of 15)").isEqualTo(35);

        // Invariant 5: no orphan zone locks (all zoneIds reference existing sections)
        long orphanLocks = zoneLockRepository.findAll().stream()
            .filter(zl -> sectionRepository.findById(zl.getZoneId()).isEmpty())
            .count();
        assertThat(orphanLocks).as("No orphan zone lock rows").isZero();

        // Invariant 6: no expired locks among active ones (each lock's expiresAt > now)
        long expiredLocks = zoneLockRepository.findAll().stream()
            .filter(zl -> zl.getExpiresAt().isBefore(LocalDateTime.now()))
            .count();
        assertThat(expiredLocks).as("No expired zone locks (all acquired recently)").isZero();

        // Invariant 7: no duplicate reservationId among active zone locks (business rule)
        List<ZoneLock> allZoneLocks = zoneLockRepository.findAll();
        long distinctReservationIds = allZoneLocks.stream().map(ZoneLock::getReservationId).distinct().count();
        assertThat(distinctReservationIds).as("No duplicate reservationId in active zone locks").isEqualTo(allZoneLocks.size());
    }

    @Test
    @DisplayName("Rollback: confirmZone deletes expired lock then throws — delete rolled back, capacity unchanged")
    void givenExpiredZoneLock_whenConfirmThrowsLockExpired_thenDeleteRolledBack() {
        Event event = createPublishedZoneEvent();
        Section section = createSection(event, 100);
        String reservationId = "rollback-expired-zone";

        lockService.acquireZoneLock(event.getId(), new LockZoneRequest(reservationId, section.getId(), 1));
        ZoneLock lock = zoneLockRepository.findByReservationId(reservationId).orElseThrow();
        lock.setExpiresAt(LocalDateTime.now().minusMinutes(5));
        zoneLockRepository.save(lock);

        assertThrows(LockExpiredException.class, () -> lockService.confirm(reservationId));

        assertThat(zoneLockRepository.findByReservationId(reservationId))
            .as("Expired zone lock still exists — transaction rolled back the delete").isPresent();
        assertThat(sectionRepository.findById(section.getId()).orElseThrow().getRemainingCapacity())
            .as("Capacity unchanged — confirm did not decrement").isEqualTo(100);
        assertThat(zoneLockRepository.count()).as("No orphan zone locks").isEqualTo(1);
    }

    @Test
    @DisplayName("Rollback: outer transaction throws after acquireZoneLock — lock creation rolled back, capacity unchanged")
    void givenZoneAcquisition_whenOuterTxRollsBack_thenLockNotPersisted() {
        Event event = createPublishedZoneEvent();
        Section section = createSection(event, 100);
        String reservationId = "rollback-acquire-zone";

        TransactionTemplate tt = new TransactionTemplate(transactionManager);
        assertThrows(RuntimeException.class, () -> tt.execute(status -> {
            lockService.acquireZoneLock(event.getId(), new LockZoneRequest(reservationId, section.getId(), 1));
            ZoneLock saved = zoneLockRepository.findByReservationId(reservationId).orElseThrow();
            assertThat(saved).as("Lock visible within the transaction before rollback").isNotNull();
            throw new RuntimeException("simulated failure after acquire");
        }));

        assertThat(zoneLockRepository.findByReservationId(reservationId))
            .as("No zone lock persisted after transaction rollback").isEmpty();
        assertThat(sectionRepository.findById(section.getId()).orElseThrow().getRemainingCapacity())
            .as("Capacity unchanged after rollback").isEqualTo(100);
    }

    @Test
    @DisplayName("Rollback: outer transaction throws after confirm(zone) — capacity decrement and lock deletion rolled back")
    void givenZoneConfirm_whenOuterTxRollsBack_thenCapacityAndDeleteReversed() {
        Event event = createPublishedZoneEvent();
        Section section = createSection(event, 100);
        String reservationId = "rollback-confirm-zone";

        lockService.acquireZoneLock(event.getId(), new LockZoneRequest(reservationId, section.getId(), 1));

        TransactionTemplate tt = new TransactionTemplate(transactionManager);
        assertThrows(RuntimeException.class, () -> tt.execute(status -> {
            lockService.confirm(reservationId);
            Section refreshed = sectionRepository.findById(section.getId()).orElseThrow();
            assertThat(refreshed.getRemainingCapacity()).as("Capacity decremented within transaction before rollback").isEqualTo(99);
            assertThat(zoneLockRepository.findByReservationId(reservationId))
                .as("Zone lock deleted within transaction before rollback").isEmpty();
            throw new RuntimeException("simulated failure after confirm");
        }));

        assertThat(sectionRepository.findById(section.getId()).orElseThrow().getRemainingCapacity())
            .as("Capacity restored after rollback").isEqualTo(100);
        assertThat(zoneLockRepository.findByReservationId(reservationId))
            .as("Zone lock restored after rollback — delete reversed").isPresent();
        assertThat(zoneLockRepository.count()).as("No orphan zone locks").isEqualTo(1);
    }

    @Test
    @DisplayName("Rollback: outer transaction throws after release(zone) — lock deletion rolled back, capacity unchanged")
    void givenZoneRelease_whenOuterTxRollsBack_thenDeleteReversed() {
        Event event = createPublishedZoneEvent();
        Section section = createSection(event, 100);
        String reservationId = "rollback-release-zone";

        lockService.acquireZoneLock(event.getId(), new LockZoneRequest(reservationId, section.getId(), 1));

        TransactionTemplate tt = new TransactionTemplate(transactionManager);
        assertThrows(RuntimeException.class, () -> tt.execute(status -> {
            lockService.release(reservationId);
            assertThat(zoneLockRepository.findByReservationId(reservationId))
                .as("Zone lock deleted within transaction before rollback").isEmpty();
            throw new RuntimeException("simulated failure after release");
        }));

        assertThat(zoneLockRepository.findByReservationId(reservationId))
            .as("Zone lock restored after rollback — delete reversed").isPresent();
        assertThat(sectionRepository.findById(section.getId()).orElseThrow().getRemainingCapacity())
            .as("Capacity unchanged after rollback").isEqualTo(100);
        assertThat(zoneLockRepository.count()).as("No orphan zone locks").isEqualTo(1);
    }
}
