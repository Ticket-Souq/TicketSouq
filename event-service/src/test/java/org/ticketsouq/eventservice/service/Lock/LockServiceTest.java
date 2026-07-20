package org.ticketsouq.eventservice.service.Lock;

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
import org.ticketsouq.eventservice.service.LockService;
import org.ticketsouq.sharedmodule.EventService.dto.LockZoneRequest;
import org.ticketsouq.sharedmodule.EventService.dto.LockZoneResponse;
import org.ticketsouq.sharedmodule.EventService.exception.*;
import org.ticketsouq.sharedmodule.GeneralExceptions.ConflictException;
import org.ticketsouq.sharedmodule.GeneralExceptions.ResourceNotFoundException;
import org.ticketsouq.sharedmodule.ReservationService.dto.ConfirmResponse;
import org.ticketsouq.sharedmodule.EventService.dto.LockSeatsRequest;
import org.ticketsouq.sharedmodule.EventService.dto.LockSeatsResponse;
import org.ticketsouq.sharedmodule.ReservationService.dto.ReleaseResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LockServiceTest {

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
    @DisplayName("Should acquire seat locks when all seats are available")
    void givenAvailableSeats_whenAcquireSeatLocks_thenSucceed() {
        UUID eventId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        LockSeatsRequest request = new LockSeatsRequest("res-1", List.of(seatId));
        Event event = Event.builder().id(eventId).status(EventStatus.PUBLISHED).bookingModel(BookingModel.SEAT).build();
        Seat seat = Seat.builder().id(seatId).status(SeatStatus.AVAILABLE).build();

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(seatRepository.findByIdInAndEventIdWithLock(List.of(seatId), eventId)).thenReturn(List.of(seat));
        when(seatLockRepository.findBySeatIdInAndExpiresAtAfter(anyList(), any())).thenReturn(List.of());

        LockSeatsResponse response = lockService.acquireSeatLocks(eventId, request);

        assertThat(response.status()).isEqualTo("LOCKED");
        assertThat(response.lockedSeats()).containsExactly(seatId);
        verify(seatLockRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when event does not exist")
    void givenNonExistentEvent_whenAcquireSeatLocks_thenThrowEventNotFound() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> lockService.acquireSeatLocks(eventId, new LockSeatsRequest("r", List.of(UUID.randomUUID()))))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should throw ConflictException when event is not published")
    void givenNonPublishedEvent_whenAcquireSeatLocks_thenThrowConflict() {
        UUID eventId = UUID.randomUUID();
        Event event = Event.builder().id(eventId).status(EventStatus.ACTIVE).bookingModel(BookingModel.SEAT).build();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> lockService.acquireSeatLocks(eventId, new LockSeatsRequest("r", List.of(UUID.randomUUID()))))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("Should throw InvalidEventTypeException when event is not seat-based")
    void givenZoneBasedEvent_whenAcquireSeatLocks_thenThrowInvalidEventType() {
        UUID eventId = UUID.randomUUID();
        Event event = Event.builder().id(eventId).status(EventStatus.PUBLISHED).bookingModel(BookingModel.ZONE).build();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> lockService.acquireSeatLocks(eventId, new LockSeatsRequest("r", List.of(UUID.randomUUID()))))
            .isInstanceOf(InvalidEventTypeException.class);
    }

    @Test
    @DisplayName("Should throw SeatNotInEventException when seats do not belong to event")
    void givenSeatsNotInEvent_whenAcquireSeatLocks_thenThrowSeatNotInEvent() {
        UUID eventId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        Event event = Event.builder().id(eventId).status(EventStatus.PUBLISHED).bookingModel(BookingModel.SEAT).build();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(seatRepository.findByIdInAndEventIdWithLock(List.of(seatId), eventId)).thenReturn(List.of());

        assertThatThrownBy(() -> lockService.acquireSeatLocks(eventId, new LockSeatsRequest("r", List.of(seatId))))
            .isInstanceOf(SeatNotInEventException.class);
    }

    @Test
    @DisplayName("Should throw SeatAlreadyBookedException when seats are already booked")
    void givenAlreadyBookedSeats_whenAcquireSeatLocks_thenThrowSeatAlreadyBooked() {
        UUID eventId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        Event event = Event.builder().id(eventId).status(EventStatus.PUBLISHED).bookingModel(BookingModel.SEAT).build();
        Seat seat = Seat.builder().id(seatId).status(SeatStatus.BOOKED).build();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(seatRepository.findByIdInAndEventIdWithLock(List.of(seatId), eventId)).thenReturn(List.of(seat));

        assertThatThrownBy(() -> lockService.acquireSeatLocks(eventId, new LockSeatsRequest("r", List.of(seatId))))
            .isInstanceOf(SeatAlreadyBookedException.class);
    }

    @Test
    @DisplayName("Should throw SeatAlreadyLockedException when seats are already locked")
    void givenAlreadyLockedSeats_whenAcquireSeatLocks_thenThrowSeatAlreadyLocked() {
        UUID eventId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        Event event = Event.builder().id(eventId).status(EventStatus.PUBLISHED).bookingModel(BookingModel.SEAT).build();
        Seat seat = Seat.builder().id(seatId).status(SeatStatus.AVAILABLE).build();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(seatRepository.findByIdInAndEventIdWithLock(List.of(seatId), eventId)).thenReturn(List.of(seat));
        when(seatLockRepository.findBySeatIdInAndExpiresAtAfter(anyList(), any()))
            .thenReturn(List.of(SeatLock.builder().seatId(seatId).build()));

        assertThatThrownBy(() -> lockService.acquireSeatLocks(eventId, new LockSeatsRequest("r", List.of(seatId))))
            .isInstanceOf(SeatAlreadyLockedException.class);
    }

    @Test
    @DisplayName("Should acquire zone lock when capacity is available")
    void givenAvailableCapacity_whenAcquireZoneLock_thenSucceed() {
        UUID eventId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        LockZoneRequest request = new LockZoneRequest("res-1", zoneId, 3);
        Event event = Event.builder().id(eventId).status(EventStatus.PUBLISHED).bookingModel(BookingModel.ZONE).build();
        Section section = Section.builder().id(zoneId).remainingCapacity(10).build();

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(sectionRepository.findByIdAndEventIdWithLock(zoneId, eventId)).thenReturn(Optional.of(section));
        when(zoneLockRepository.sumActiveQuantityByZoneId(eq(zoneId), any())).thenReturn(2);

        LockZoneResponse response = lockService.acquireZoneLock(eventId, request);

        assertThat(response.status()).isEqualTo("LOCKED");
        assertThat(response.quantity()).isEqualTo(3);
        verify(zoneLockRepository).save(any(ZoneLock.class));
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when acquiring zone lock for non-existent event")
    void givenNonExistentEvent_whenAcquireZoneLock_thenThrowEventNotFound() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> lockService.acquireZoneLock(eventId, new LockZoneRequest("r", UUID.randomUUID(), 1)))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should throw ConflictException when acquiring zone lock for non-published event")
    void givenNonPublishedEvent_whenAcquireZoneLock_thenThrowConflict() {
        UUID eventId = UUID.randomUUID();
        Event event = Event.builder().id(eventId).status(EventStatus.ACTIVE).bookingModel(BookingModel.ZONE).build();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> lockService.acquireZoneLock(eventId, new LockZoneRequest("r", UUID.randomUUID(), 1)))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("Should throw InvalidEventTypeException when acquiring zone lock for seat-based event")
    void givenSeatBasedEvent_whenAcquireZoneLock_thenThrowInvalidEventType() {
        UUID eventId = UUID.randomUUID();
        Event event = Event.builder().id(eventId).status(EventStatus.PUBLISHED).bookingModel(BookingModel.SEAT).build();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> lockService.acquireZoneLock(eventId, new LockZoneRequest("r", UUID.randomUUID(), 1)))
            .isInstanceOf(InvalidEventTypeException.class);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when zone section is not found")
    void givenNonExistentSection_whenAcquireZoneLock_thenThrowResourceNotFound() {
        UUID eventId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        Event event = Event.builder().id(eventId).status(EventStatus.PUBLISHED).bookingModel(BookingModel.ZONE).build();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(sectionRepository.findByIdAndEventIdWithLock(zoneId, eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> lockService.acquireZoneLock(eventId, new LockZoneRequest("r", zoneId, 1)))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should throw ZoneCapacityExceededException when zone capacity is exceeded")
    void givenCapacityExceeded_whenAcquireZoneLock_thenThrowZoneCapacityExceeded() {
        UUID eventId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        Event event = Event.builder().id(eventId).status(EventStatus.PUBLISHED).bookingModel(BookingModel.ZONE).build();
        Section section = Section.builder().id(zoneId).remainingCapacity(5).build();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(sectionRepository.findByIdAndEventIdWithLock(zoneId, eventId)).thenReturn(Optional.of(section));
        when(zoneLockRepository.sumActiveQuantityByZoneId(eq(zoneId), any())).thenReturn(4);

        assertThatThrownBy(() -> lockService.acquireZoneLock(eventId, new LockZoneRequest("r", zoneId, 3)))
            .isInstanceOf(ZoneCapacityExceededException.class);
    }

    @Test
    @DisplayName("Should return CONFIRMED when no locks exist for reservation")
    void givenNoLocks_whenConfirm_thenReturnConfirmed() {
        String reservationId = "res-1";
        when(seatLockRepository.findByReservationIdWithLock(reservationId)).thenReturn(List.of());
        when(zoneLockRepository.findByReservationIdWithLock(reservationId)).thenReturn(Optional.empty());

        ConfirmResponse response = lockService.confirm(reservationId);

        assertThat(response).isEqualTo(ConfirmResponse.CONFIRMED);
    }

    @Test
    @DisplayName("Should confirm and book seats when seat locks are valid")
    void givenValidSeatLocks_whenConfirm_thenBookSeats() {
        String reservationId = "res-1";
        UUID seatId = UUID.randomUUID();
        SeatLock seatLock = SeatLock.builder()
            .seatId(seatId)
            .reservationId(reservationId)
            .expiresAt(LocalDateTime.now().plusMinutes(5))
            .build();
        Seat seat = Seat.builder().id(seatId).status(SeatStatus.AVAILABLE).build();

        when(seatLockRepository.findByReservationIdWithLock(reservationId)).thenReturn(List.of(seatLock));
        when(seatRepository.findByIdInWithLock(List.of(seatId))).thenReturn(List.of(seat));

        ConfirmResponse response = lockService.confirm(reservationId);

        assertThat(response).isEqualTo(ConfirmResponse.CONFIRMED);
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.BOOKED);
        verify(seatRepository).saveAll(List.of(seat));
        verify(seatLockRepository).deleteByReservationId(reservationId);
    }

    @Test
    @DisplayName("Should throw LockExpiredException when seat locks have expired")
    void givenExpiredSeatLocks_whenConfirm_thenThrowLockExpired() {
        String reservationId = "res-1";
        SeatLock seatLock = SeatLock.builder()
            .seatId(UUID.randomUUID())
            .reservationId(reservationId)
            .expiresAt(LocalDateTime.now().minusMinutes(1))
            .build();

        when(seatLockRepository.findByReservationIdWithLock(reservationId)).thenReturn(List.of(seatLock));

        assertThatThrownBy(() -> lockService.confirm(reservationId))
            .isInstanceOf(LockExpiredException.class);
        verify(seatLockRepository).deleteByReservationId(reservationId);
    }

    @Test
    @DisplayName("Should throw SeatAlreadyBookedException when seat was booked during lock")
    void givenSeatBookedDuringLock_whenConfirm_thenThrowSeatAlreadyBooked() {
        String reservationId = "res-1";
        UUID seatId = UUID.randomUUID();
        SeatLock seatLock = SeatLock.builder()
            .seatId(seatId)
            .reservationId(reservationId)
            .expiresAt(LocalDateTime.now().plusMinutes(5))
            .build();
        Seat seat = Seat.builder().id(seatId).status(SeatStatus.BOOKED).build();

        when(seatLockRepository.findByReservationIdWithLock(reservationId)).thenReturn(List.of(seatLock));
        when(seatRepository.findByIdInWithLock(List.of(seatId))).thenReturn(List.of(seat));

        assertThatThrownBy(() -> lockService.confirm(reservationId))
            .isInstanceOf(SeatAlreadyBookedException.class);
    }

    @Test
    @DisplayName("Should confirm zone lock and deduct capacity")
    void givenValidZoneLock_whenConfirm_thenDeductCapacity() {
        String reservationId = "res-1";
        UUID zoneId = UUID.randomUUID();
        ZoneLock zoneLock = ZoneLock.builder()
            .zoneId(zoneId)
            .reservationId(reservationId)
            .quantity(3)
            .expiresAt(LocalDateTime.now().plusMinutes(5))
            .build();
        Section section = Section.builder().id(zoneId).remainingCapacity(10).build();

        when(seatLockRepository.findByReservationIdWithLock(reservationId)).thenReturn(List.of());
        when(zoneLockRepository.findByReservationIdWithLock(reservationId)).thenReturn(Optional.of(zoneLock));
        when(sectionRepository.findByIdWithLock(zoneId)).thenReturn(Optional.of(section));

        ConfirmResponse response = lockService.confirm(reservationId);

        assertThat(response).isEqualTo(ConfirmResponse.CONFIRMED);
        assertThat(section.getRemainingCapacity()).isEqualTo(7);
        verify(zoneLockRepository).deleteByReservationId(reservationId);
    }

    @Test
    @DisplayName("Should throw LockExpiredException when zone lock has expired")
    void givenExpiredZoneLock_whenConfirm_thenThrowLockExpired() {
        String reservationId = "res-1";
        ZoneLock zoneLock = ZoneLock.builder()
            .zoneId(UUID.randomUUID())
            .reservationId(reservationId)
            .expiresAt(LocalDateTime.now().minusMinutes(1))
            .build();

        when(seatLockRepository.findByReservationIdWithLock(reservationId)).thenReturn(List.of());
        when(zoneLockRepository.findByReservationIdWithLock(reservationId)).thenReturn(Optional.of(zoneLock));

        assertThatThrownBy(() -> lockService.confirm(reservationId))
            .isInstanceOf(LockExpiredException.class);
        verify(zoneLockRepository).deleteByReservationId(reservationId);
    }

    @Test
    @DisplayName("Should delete all locks when releasing a reservation")
    void givenReservationId_whenRelease_thenDeleteAllLocks() {
        String reservationId = "res-1";

        ReleaseResponse response = lockService.release(reservationId);

        assertThat(response).isEqualTo(ReleaseResponse.RELEASED);
        verify(seatLockRepository).deleteByReservationId(reservationId);
        verify(zoneLockRepository).deleteByReservationId(reservationId);
    }

    @Test
    @DisplayName("Should return zone statuses with correct counts")
    void givenEventWithZones_whenGetZoneStatuses_thenReturnCorrectCounts() {
        UUID eventId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        Section section = Section.builder()
            .id(zoneId)
            .name("VIP")
            .capacity(100)
            .remainingCapacity(80)
            .build();
        Event event = Event.builder()
            .id(eventId)
            .bookingModel(BookingModel.ZONE)
            .sections(List.of(section))
            .build();

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(zoneLockRepository.sumActiveQuantityByZoneId(eq(zoneId), any())).thenReturn(5);

        List<ZoneStatusResponse> responses = lockService.getZoneStatuses(eventId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).zoneId()).isEqualTo(zoneId);
        assertThat(responses.get(0).capacity()).isEqualTo(100);
        assertThat(responses.get(0).booked()).isEqualTo(20);
        assertThat(responses.get(0).locked()).isEqualTo(5);
        assertThat(responses.get(0).available()).isEqualTo(75);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when getting zone statuses for non-existent event")
    void givenNonExistentEvent_whenGetZoneStatuses_thenThrowEventNotFound() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> lockService.getZoneStatuses(eventId))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should throw InvalidEventTypeException when getting zone statuses for seat-based event")
    void givenSeatBasedEvent_whenGetZoneStatuses_thenThrowInvalidEventType() {
        UUID eventId = UUID.randomUUID();
        Event event = Event.builder().id(eventId).bookingModel(BookingModel.SEAT).build();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> lockService.getZoneStatuses(eventId))
            .isInstanceOf(InvalidEventTypeException.class);
    }

    @Test
    @DisplayName("Should return empty list when zone-based event has no sections")
    void givenZoneEventWithoutSections_whenGetZoneStatuses_thenReturnEmpty() {
        UUID eventId = UUID.randomUUID();
        Event event = Event.builder().id(eventId).bookingModel(BookingModel.ZONE).build();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        List<ZoneStatusResponse> responses = lockService.getZoneStatuses(eventId);

        assertThat(responses).isEmpty();
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when section deleted before zone confirm")
    void givenDeletedSection_whenConfirmZoneLock_thenThrowResourceNotFound() {
        String reservationId = "res-1";
        UUID zoneId = UUID.randomUUID();
        ZoneLock zoneLock = ZoneLock.builder()
            .zoneId(zoneId)
            .reservationId(reservationId)
            .quantity(2)
            .expiresAt(LocalDateTime.now().plusMinutes(5))
            .build();

        when(seatLockRepository.findByReservationIdWithLock(reservationId)).thenReturn(List.of());
        when(zoneLockRepository.findByReservationIdWithLock(reservationId)).thenReturn(Optional.of(zoneLock));
        when(sectionRepository.findByIdWithLock(zoneId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> lockService.confirm(reservationId))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
