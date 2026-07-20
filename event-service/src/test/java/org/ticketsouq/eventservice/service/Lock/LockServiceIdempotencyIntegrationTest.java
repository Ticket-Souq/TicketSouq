package org.ticketsouq.eventservice.service.Lock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.ticketsouq.eventservice.model.*;
import org.ticketsouq.eventservice.model.enums.SeatStatus;
import org.ticketsouq.sharedmodule.EventService.dto.LockSeatsRequest;
import org.ticketsouq.sharedmodule.EventService.dto.LockZoneRequest;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LockServiceIdempotencyIntegrationTest extends LockServiceIntegrationTestBase {

    @Test
    @DisplayName("Confirm idempotency: seat — second confirm must not corrupt state")
    void givenSeatReservation_whenConfirmTwice_thenStatePreserved() {
        Event event = createPublishedSeatEvent();
        Section section = createSection(event, 10);
        UUID seatId = UUID.randomUUID();
        createSeat(section, seatId, SeatStatus.AVAILABLE);
        String reservationId = "confirm-idem-seat";

        lockService.acquireSeatLocks(event.getId(), new LockSeatsRequest(reservationId, List.of(seatId)));

        lockService.confirm(reservationId);
        Seat seat = seatRepository.findById(seatId).orElseThrow();
        assertThat(seat.getStatus()).as("Seat booked after first confirm").isEqualTo(SeatStatus.BOOKED);
        assertThat(seatLockRepository.findByReservationId(reservationId)).as("No seat locks after first confirm").isEmpty();

        lockService.confirm(reservationId);
        seat = seatRepository.findById(seatId).orElseThrow();
        assertThat(seat.getStatus()).as("Seat still booked after second confirm").isEqualTo(SeatStatus.BOOKED);
        assertThat(seatLockRepository.findByReservationId(reservationId)).as("No seat locks after second confirm").isEmpty();
    }

    @Test
    @DisplayName("Confirm idempotency: zone — second confirm must not corrupt capacity or leave locks")
    void givenZoneReservation_whenConfirmTwice_thenStatePreserved() {
        Event event = createPublishedZoneEvent();
        Section section = createSection(event, 100);
        String reservationId = "confirm-idem-zone";

        lockService.acquireZoneLock(event.getId(), new LockZoneRequest(reservationId, section.getId(), 1));

        lockService.confirm(reservationId);
        Section refreshed = sectionRepository.findById(section.getId()).orElseThrow();
        assertThat(refreshed.getRemainingCapacity()).as("Capacity consumed after first confirm").isEqualTo(99);
        assertThat(zoneLockRepository.findByReservationId(reservationId)).as("No zone locks after first confirm").isEmpty();

        lockService.confirm(reservationId);
        refreshed = sectionRepository.findById(section.getId()).orElseThrow();
        assertThat(refreshed.getRemainingCapacity()).as("Capacity unchanged after second confirm").isEqualTo(99);
        assertThat(zoneLockRepository.findByReservationId(reservationId)).as("No zone locks after second confirm").isEmpty();
    }

    @Test
    @DisplayName("Release idempotency: seat and zone — second release must not corrupt state")
    void givenReservation_whenReleaseTwice_thenStatePreserved() {
        Event seatEvent = createPublishedSeatEvent();
        Section seatSection = createSection(seatEvent, 10);
        UUID seatId = UUID.randomUUID();
        createSeat(seatSection, seatId, SeatStatus.AVAILABLE);
        String seatResId = "release-idem-seat";

        lockService.acquireSeatLocks(seatEvent.getId(), new LockSeatsRequest(seatResId, List.of(seatId)));
        assertThat(seatLockRepository.findByReservationId(seatResId)).isNotEmpty();

        lockService.release(seatResId);
        assertThat(seatLockRepository.findByReservationId(seatResId)).as("No seat locks after first release").isEmpty();
        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus()).as("Seat AVAILABLE after release").isEqualTo(SeatStatus.AVAILABLE);

        lockService.release(seatResId);
        assertThat(seatLockRepository.findByReservationId(seatResId)).as("No seat locks after second release").isEmpty();
        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus()).as("Seat still AVAILABLE after second release").isEqualTo(SeatStatus.AVAILABLE);

        Event zoneEvent = createPublishedZoneEvent();
        Section zoneSection = createSection(zoneEvent, 100);
        String zoneResId = "release-idem-zone";

        lockService.acquireZoneLock(zoneEvent.getId(), new LockZoneRequest(zoneResId, zoneSection.getId(), 5));
        assertThat(zoneLockRepository.findByReservationId(zoneResId)).isPresent();

        lockService.release(zoneResId);
        assertThat(zoneLockRepository.findByReservationId(zoneResId)).as("No zone locks after first release").isEmpty();
        assertThat(sectionRepository.findById(zoneSection.getId()).orElseThrow().getRemainingCapacity()).as("Capacity unchanged after release").isEqualTo(100);

        lockService.release(zoneResId);
        assertThat(zoneLockRepository.findByReservationId(zoneResId)).as("No zone locks after second release").isEmpty();
        assertThat(sectionRepository.findById(zoneSection.getId()).orElseThrow().getRemainingCapacity()).as("Capacity still unchanged after second release").isEqualTo(100);
    }

    @Test
    @DisplayName("Idempotency: confirm() without prior acquire — no state corruption, no orphan locks")
    void givenUnknownReservation_whenConfirmCalled_thenNoSideEffects() {
        // Production bug: A caller or retry mechanism might invoke confirm() for
        // a reservation that never acquired locks (e.g. after TTL cleanup, or
        // a duplicate RPC with a stale reservation ID). If confirm() blindly
        // updates state, it could corrupt seat status or capacity.
        // Business invariant: confirm() at the "no locks exist" boundary is a
        // safe no-op — it returns CONFIRMED without any side effects.

        Event seatEvent = createPublishedSeatEvent();
        Section seatSection = createSection(seatEvent, 10);
        UUID seatId = UUID.randomUUID();
        createSeat(seatSection, seatId, SeatStatus.AVAILABLE);

        lockService.confirm("confirm-without-acquire-seat");

        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus())
            .as("Seat untouched by confirm without acquire").isEqualTo(SeatStatus.AVAILABLE);
        assertThat(seatLockRepository.count()).as("No seat locks created").isZero();

        Event zoneEvent = createPublishedZoneEvent();
        Section zoneSection = createSection(zoneEvent, 100);

        lockService.confirm("confirm-without-acquire-zone");

        assertThat(sectionRepository.findById(zoneSection.getId()).orElseThrow().getRemainingCapacity())
            .as("Capacity untouched by confirm without acquire").isEqualTo(100);
        assertThat(zoneLockRepository.count()).as("No zone locks created").isZero();
    }

    @Test
    @DisplayName("Idempotency: release() without prior acquire — no state corruption, no orphan locks")
    void givenUnknownReservation_whenReleaseCalled_thenNoSideEffects() {
        // Production bug: A cleanup job or a manual cancellation might call
        // release() for a reservation that already expired or was already
        // released. If release() corrupts state on an empty reservation,
        // seat status or capacity could drift.
        // Business invariant: release() is always safe — it is a no-op when
        // no locks exist for the given reservation ID.

        Event seatEvent = createPublishedSeatEvent();
        Section seatSection = createSection(seatEvent, 10);
        UUID seatId = UUID.randomUUID();
        createSeat(seatSection, seatId, SeatStatus.AVAILABLE);

        lockService.release("release-without-acquire-seat");

        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus())
            .as("Seat untouched by release without acquire").isEqualTo(SeatStatus.AVAILABLE);
        assertThat(seatLockRepository.count()).as("No seat locks created").isZero();

        Event zoneEvent = createPublishedZoneEvent();
        Section zoneSection = createSection(zoneEvent, 100);

        lockService.release("release-without-acquire-zone");

        assertThat(sectionRepository.findById(zoneSection.getId()).orElseThrow().getRemainingCapacity())
            .as("Capacity untouched by release without acquire").isEqualTo(100);
        assertThat(zoneLockRepository.count()).as("No zone locks created").isZero();
    }

    @Test
    @DisplayName("State transition: seat acquire -> confirm -> release — release after confirm does not revert booking")
    void givenSeatReservation_whenConfirmThenRelease_thenBookingPreserved() {
        // Production bug: A "cancel" or "timeout" flow that runs after a
        // successful confirm must not silently undo the booking. If release()
        // blindly deletes the entity and flips the seat back to AVAILABLE,
        // a confirmed ticket would disappear.
        // Business invariant: confirm() is terminal — once a seat transitions
        // to BOOKED, only an explicit refund/rollback (a different operation)
        // should revert it. release() after confirm() is a no-op.

        Event event = createPublishedSeatEvent();
        Section section = createSection(event, 10);
        UUID seatId = UUID.randomUUID();
        createSeat(section, seatId, SeatStatus.AVAILABLE);
        String reservationId = "seat-confirm-then-release";

        lockService.acquireSeatLocks(event.getId(), new LockSeatsRequest(reservationId, List.of(seatId)));
        lockService.confirm(reservationId);

        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus())
            .as("Seat BOOKED after confirm").isEqualTo(SeatStatus.BOOKED);
        assertThat(seatLockRepository.findByReservationId(reservationId))
            .as("No seat locks after confirm").isEmpty();

        lockService.release(reservationId);

        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus())
            .as("Seat remains BOOKED after release following confirm").isEqualTo(SeatStatus.BOOKED);
        assertThat(seatLockRepository.count()).as("No orphan seat locks").isZero();
    }

    @Test
    @DisplayName("State transition: zone acquire -> confirm -> release — capacity unchanged after release following confirm")
    void givenZoneReservation_whenConfirmThenRelease_thenCapacityPreserved() {
        // Production bug: Same as the seat variant — release after confirm must
        // not restore remainingCapacity. The capacity was already consumed
        // atomically by confirm's commit.
        // Business invariant: remainingCapacity tracks confirmed consumption,
        // not lock state. release() after confirm() is a no-op on capacity.

        Event event = createPublishedZoneEvent();
        Section section = createSection(event, 100);
        String reservationId = "zone-confirm-then-release";

        lockService.acquireZoneLock(event.getId(), new LockZoneRequest(reservationId, section.getId(), 1));
        lockService.confirm(reservationId);

        assertThat(sectionRepository.findById(section.getId()).orElseThrow().getRemainingCapacity())
            .as("Capacity consumed after confirm").isEqualTo(99);
        assertThat(zoneLockRepository.findByReservationId(reservationId))
            .as("No zone locks after confirm").isEmpty();

        lockService.release(reservationId);

        assertThat(sectionRepository.findById(section.getId()).orElseThrow().getRemainingCapacity())
            .as("Capacity unchanged after release following confirm").isEqualTo(99);
        assertThat(zoneLockRepository.count()).as("No orphan zone locks").isZero();
    }

    @Test
    @DisplayName("State transition: seat acquire -> release -> confirm — confirm after release does not book seat")
    void givenSeatReservation_whenReleaseThenConfirm_thenSeatStaysAvailable() {
        // Production bug: A concurrent flow might call confirm() on a reservation
        // that another flow already released (or that timed out and was released
        // by cleanup). If confirm() books the seat despite the locks being gone,
        // a phantom booking would appear.
        // Business invariant: release() fully dismantles the reservation.
        // confirm() on a released reservation is idempotent — locks must exist
        // for a booking to occur.

        Event event = createPublishedSeatEvent();
        Section section = createSection(event, 10);
        UUID seatId = UUID.randomUUID();
        createSeat(section, seatId, SeatStatus.AVAILABLE);
        String reservationId = "seat-release-then-confirm";

        lockService.acquireSeatLocks(event.getId(), new LockSeatsRequest(reservationId, List.of(seatId)));
        lockService.release(reservationId);

        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus())
            .as("Seat AVAILABLE after release").isEqualTo(SeatStatus.AVAILABLE);
        assertThat(seatLockRepository.findByReservationId(reservationId))
            .as("No seat locks after release").isEmpty();

        lockService.confirm(reservationId);

        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus())
            .as("Seat remains AVAILABLE after confirm following release").isEqualTo(SeatStatus.AVAILABLE);
        assertThat(seatLockRepository.count()).as("No orphan seat locks").isZero();
    }

    @Test
    @DisplayName("State transition: zone acquire -> release -> confirm — confirm after release does not consume capacity")
    void givenZoneReservation_whenReleaseThenConfirm_thenCapacityPreserved() {
        // Production bug: Same as the seat variant — confirm after release must
        // not re-consume capacity. Locks are the prerequisite for confirmation;
        // without locks, confirm must be a no-op.
        // Business invariant: release() fully returns capacity to the pool.
        // confirm() on a released reservation is idempotent and does not
        // re-decrement remainingCapacity.

        Event event = createPublishedZoneEvent();
        Section section = createSection(event, 100);
        String reservationId = "zone-release-then-confirm";

        lockService.acquireZoneLock(event.getId(), new LockZoneRequest(reservationId, section.getId(), 1));
        lockService.release(reservationId);

        assertThat(sectionRepository.findById(section.getId()).orElseThrow().getRemainingCapacity())
            .as("Capacity restored after release").isEqualTo(100);
        assertThat(zoneLockRepository.findByReservationId(reservationId))
            .as("No zone locks after release").isEmpty();

        lockService.confirm(reservationId);

        assertThat(sectionRepository.findById(section.getId()).orElseThrow().getRemainingCapacity())
            .as("Capacity unchanged after confirm following release").isEqualTo(100);
        assertThat(zoneLockRepository.count()).as("No orphan zone locks").isZero();
    }
}
