package org.ticketsouq.eventservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.ticketsouq.eventservice.dto.SeatResponse;
import org.ticketsouq.eventservice.dto.UpdateSeatStatusRequest;
import org.ticketsouq.eventservice.model.Event;
import org.ticketsouq.eventservice.model.Seat;
import org.ticketsouq.eventservice.model.Section;
import org.ticketsouq.eventservice.model.enums.BookingModel;
import org.ticketsouq.eventservice.model.enums.EventStatus;
import org.ticketsouq.eventservice.model.enums.SeatStatus;
import org.ticketsouq.eventservice.repository.SeatRepository;
import org.ticketsouq.sharedmodule.AuditService.events.AuditEvent;
import org.ticketsouq.sharedmodule.GeneralExceptions.BadRequestException;
import org.ticketsouq.sharedmodule.GeneralExceptions.ConflictException;
import org.ticketsouq.sharedmodule.GeneralExceptions.ResourceNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeatServiceTest {

    @Mock private SeatRepository seatRepository;
    @Mock private ApplicationEventPublisher applicationEventPublisher;

    private SeatService seatService;

    @BeforeEach
    void setUp() {
        seatService = new SeatService(seatRepository, applicationEventPublisher);
    }

    @Test
    @DisplayName("Should succeed when changing seat from AVAILABLE to BOOKED_ORGANIZER")
    void givenAvailableSeat_whenUpdateToBookedOrganizer_thenSucceed() {
        UUID seatId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID sectionId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Event event = Event.builder()
            .id(eventId)
            .status(EventStatus.PUBLISHED)
            .bookingModel(BookingModel.SEAT)
            .build();
        Section section = Section.builder()
            .id(sectionId)
            .event(event)
            .remainingCapacity(100)
            .build();
        Seat seat = Seat.builder()
            .id(seatId)
            .section(section)
            .status(SeatStatus.AVAILABLE)
            .build();

        when(seatRepository.findByIdWithSectionAndEvent(seatId)).thenReturn(Optional.of(seat));
        when(seatRepository.save(seat)).thenReturn(seat);

        SeatResponse response = seatService.updateOrganizerSeatStatus(
            seatId, new UpdateSeatStatusRequest(SeatStatus.BOOKED_ORGANIZER), userId);

        assertThat(response.status()).isEqualTo(SeatStatus.BOOKED_ORGANIZER);
        assertThat(section.getRemainingCapacity()).isEqualTo(99);
        verify(applicationEventPublisher).publishEvent(any(AuditEvent.class));
    }

    @Test
    @DisplayName("Should succeed when changing seat from BOOKED_ORGANIZER to AVAILABLE")
    void givenBookedOrganizerSeat_whenUpdateToAvailable_thenSucceed() {
        UUID seatId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID sectionId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Event event = Event.builder()
            .id(eventId)
            .status(EventStatus.PUBLISHED)
            .bookingModel(BookingModel.SEAT)
            .build();
        Section section = Section.builder()
            .id(sectionId)
            .event(event)
            .remainingCapacity(99)
            .build();
        Seat seat = Seat.builder()
            .id(seatId)
            .section(section)
            .status(SeatStatus.BOOKED_ORGANIZER)
            .build();

        when(seatRepository.findByIdWithSectionAndEvent(seatId)).thenReturn(Optional.of(seat));
        when(seatRepository.save(seat)).thenReturn(seat);

        SeatResponse response = seatService.updateOrganizerSeatStatus(
            seatId, new UpdateSeatStatusRequest(SeatStatus.AVAILABLE), userId);

        assertThat(response.status()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(section.getRemainingCapacity()).isEqualTo(100);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when seat does not exist")
    void givenNonExistentSeat_whenUpdateStatus_thenThrowResourceNotFound() {
        UUID seatId = UUID.randomUUID();
        when(seatRepository.findByIdWithSectionAndEvent(seatId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> seatService.updateOrganizerSeatStatus(
            seatId, new UpdateSeatStatusRequest(SeatStatus.BOOKED_ORGANIZER), UUID.randomUUID()))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should throw ConflictException when event is not published")
    void givenEventNotPublished_whenUpdateSeatStatus_thenThrowConflict() {
        UUID seatId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Event event = Event.builder()
            .id(eventId)
            .status(EventStatus.ACTIVE)
            .bookingModel(BookingModel.SEAT)
            .build();
        Section section = Section.builder()
            .id(UUID.randomUUID())
            .event(event)
            .build();
        Seat seat = Seat.builder()
            .id(seatId)
            .section(section)
            .status(SeatStatus.AVAILABLE)
            .build();

        when(seatRepository.findByIdWithSectionAndEvent(seatId)).thenReturn(Optional.of(seat));

        assertThatThrownBy(() -> seatService.updateOrganizerSeatStatus(
            seatId, new UpdateSeatStatusRequest(SeatStatus.BOOKED_ORGANIZER), UUID.randomUUID()))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("Should throw BadRequestException when event is not seat-based")
    void givenNonSeatBasedEvent_whenUpdateSeatStatus_thenThrowBadRequest() {
        UUID seatId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Event event = Event.builder()
            .id(eventId)
            .status(EventStatus.PUBLISHED)
            .bookingModel(BookingModel.ZONE)
            .build();
        Section section = Section.builder()
            .id(UUID.randomUUID())
            .event(event)
            .build();
        Seat seat = Seat.builder()
            .id(seatId)
            .section(section)
            .status(SeatStatus.AVAILABLE)
            .build();

        when(seatRepository.findByIdWithSectionAndEvent(seatId)).thenReturn(Optional.of(seat));

        assertThatThrownBy(() -> seatService.updateOrganizerSeatStatus(
            seatId, new UpdateSeatStatusRequest(SeatStatus.BOOKED_ORGANIZER), UUID.randomUUID()))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("Should throw BadRequestException when transitioning to an invalid status")
    void givenSeatWithInvalidTransition_whenUpdateStatus_thenThrowBadRequest() {
        UUID seatId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Event event = Event.builder()
            .id(eventId)
            .status(EventStatus.PUBLISHED)
            .bookingModel(BookingModel.SEAT)
            .build();
        Section section = Section.builder()
            .id(UUID.randomUUID())
            .event(event)
            .build();
        Seat seat = Seat.builder()
            .id(seatId)
            .section(section)
            .status(SeatStatus.AVAILABLE)
            .build();

        when(seatRepository.findByIdWithSectionAndEvent(seatId)).thenReturn(Optional.of(seat));

        assertThatThrownBy(() -> seatService.updateOrganizerSeatStatus(
            seatId, new UpdateSeatStatusRequest(SeatStatus.BOOKED), UUID.randomUUID()))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("Should throw BadRequestException when transitioning to the same status")
    void givenSameStatus_whenUpdateSeatStatus_thenThrowBadRequest() {
        UUID seatId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Event event = Event.builder()
            .id(eventId)
            .status(EventStatus.PUBLISHED)
            .bookingModel(BookingModel.SEAT)
            .build();
        Section section = Section.builder()
            .id(UUID.randomUUID())
            .event(event)
            .build();
        Seat seat = Seat.builder()
            .id(seatId)
            .section(section)
            .status(SeatStatus.AVAILABLE)
            .build();

        when(seatRepository.findByIdWithSectionAndEvent(seatId)).thenReturn(Optional.of(seat));

        assertThatThrownBy(() -> seatService.updateOrganizerSeatStatus(
            seatId, new UpdateSeatStatusRequest(SeatStatus.AVAILABLE), UUID.randomUUID()))
            .isInstanceOf(BadRequestException.class);
    }

}
