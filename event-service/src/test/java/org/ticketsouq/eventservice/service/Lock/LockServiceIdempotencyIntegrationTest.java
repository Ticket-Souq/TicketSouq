package org.ticketsouq.eventservice.service.Lock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.ticketsouq.eventservice.model.*;
import org.ticketsouq.eventservice.model.enums.SeatStatus;
import org.ticketsouq.sharedmodule.EventService.dto.LockSeatsRequest;
import org.ticketsouq.sharedmodule.EventService.dto.LockSeatsResponse;
import org.ticketsouq.sharedmodule.EventService.dto.LockZoneRequest;
import org.ticketsouq.sharedmodule.EventService.dto.LockZoneResponse;

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

        LockSeatsResponse lockRes = lockService.acquireSeatLocks(event.getId(), new LockSeatsRequest(List.of(seatId)));
        String reservationId = lockRes.reservationId().toString();

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

        LockZoneResponse lockRes = lockService.acquireZoneLock(event.getId(), new LockZoneRequest(section.getId(), 1));
        String reservationId = lockRes.reservationId().toString();

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

        LockSeatsResponse seatLockRes = lockService.acquireSeatLocks(seatEvent.getId(), new LockSeatsRequest(List.of(seatId)));
        String seatResId = seatLockRes.reservationId().toString();
        assertThat(seatLockRepository.findByReservationId(seatResId)).isNotEmpty();

        lockService.release(seatResId);
        assertThat(seatLockRepository.findByReservationId(seatResId)).as("No seat locks after first release").isEmpty();
        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus()).as("Seat AVAILABLE after release").isEqualTo(SeatStatus.AVAILABLE);

        lockService.release(seatResId);
        assertThat(seatLockRepository.findByReservationId(seatResId)).as("No seat locks after second release").isEmpty();
        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus()).as("Seat still AVAILABLE after second release").isEqualTo(SeatStatus.AVAILABLE);

        Event zoneEvent = createPublishedZoneEvent();
        Section zoneSection = createSection(zoneEvent, 100);

        LockZoneResponse zoneLockRes = lockService.acquireZoneLock(zoneEvent.getId(), new LockZoneRequest(zoneSection.getId(), 5));
        String zoneResId = zoneLockRes.reservationId().toString();
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
        Event event = createPublishedSeatEvent();
        Section section = createSection(event, 10);
        UUID seatId = UUID.randomUUID();
        createSeat(section, seatId, SeatStatus.AVAILABLE);

        LockSeatsResponse lockRes = lockService.acquireSeatLocks(event.getId(), new LockSeatsRequest(List.of(seatId)));
        String reservationId = lockRes.reservationId().toString();
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
        Event event = createPublishedZoneEvent();
        Section section = createSection(event, 100);

        LockZoneResponse lockRes = lockService.acquireZoneLock(event.getId(), new LockZoneRequest(section.getId(), 1));
        String reservationId = lockRes.reservationId().toString();
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
        Event event = createPublishedSeatEvent();
        Section section = createSection(event, 10);
        UUID seatId = UUID.randomUUID();
        createSeat(section, seatId, SeatStatus.AVAILABLE);

        LockSeatsResponse lockRes = lockService.acquireSeatLocks(event.getId(), new LockSeatsRequest(List.of(seatId)));
        String reservationId = lockRes.reservationId().toString();
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
        Event event = createPublishedZoneEvent();
        Section section = createSection(event, 100);

        LockZoneResponse lockRes = lockService.acquireZoneLock(event.getId(), new LockZoneRequest(section.getId(), 1));
        String reservationId = lockRes.reservationId().toString();
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
