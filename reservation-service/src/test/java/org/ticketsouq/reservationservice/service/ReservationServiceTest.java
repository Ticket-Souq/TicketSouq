package org.ticketsouq.reservationservice.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ticketsouq.reservationservice.dto.ReservationContext;
import org.ticketsouq.reservationservice.dto.ReservationResponse;
import org.ticketsouq.reservationservice.mapper.ReservationMapper;
import org.ticketsouq.reservationservice.model.Reservation;
import org.ticketsouq.reservationservice.repository.ReservationRepository;
import org.ticketsouq.sharedmodule.EventService.dto.TicketReservationDto;
import org.ticketsouq.sharedmodule.EventService.events.BeginReservationEvent;
import org.ticketsouq.sharedmodule.ReservationService.enums.ReservationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationMapper reservationMapper;

    @InjectMocks
    private ReservationService reservationService;

    @Test
    @DisplayName("New reservation: saves and returns when not a duplicate")
    void createReservation_newReservation_savesAndReturns() {
        UUID reservationId = UUID.randomUUID();
        BeginReservationEvent event = buildEvent(reservationId);

        Reservation expectedReservation = Reservation.builder()
            .id(reservationId)
            .userId(event.userId())
            .eventId(event.eventId())
            .status(ReservationStatus.PENDING)
            .createdAt(Instant.now())
            .build();

        when(reservationRepository.existsById(reservationId)).thenReturn(false);
        when(reservationMapper.createReservation(event)).thenReturn(expectedReservation);
        when(reservationRepository.save(any(Reservation.class))).thenReturn(expectedReservation);

        Reservation result = reservationService.createReservation(event);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(reservationId);
        assertThat(result.getStatus()).isEqualTo(ReservationStatus.PENDING);
        verify(reservationRepository).existsById(reservationId);
        verify(reservationMapper).createReservation(event);
        verify(reservationRepository).save(expectedReservation);
    }

    @Test
    @DisplayName("Duplicate reservation: returns null, does not save")
    void createReservation_duplicateReservation_returnsNull() {
        UUID reservationId = UUID.randomUUID();
        BeginReservationEvent event = buildEvent(reservationId);

        when(reservationRepository.existsById(reservationId)).thenReturn(true);

        Reservation result = reservationService.createReservation(event);

        assertThat(result).isNull();
        verify(reservationRepository).existsById(reservationId);
        verify(reservationMapper, never()).createReservation(any());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Get reservations by user with no results: returns empty list")
    void getReservationsByUser_emptyList_returnsEmpty() {
        UUID userId = UUID.randomUUID();
        when(reservationRepository.findByUserId(userId)).thenReturn(List.of());

        List<ReservationResponse> results = reservationService.getReservationsByUser(userId);

        assertThat(results).isEmpty();
    }

    private BeginReservationEvent buildEvent(UUID reservationId) {
        List<TicketReservationDto> tickets = List.of(
            new TicketReservationDto(new BigDecimal("50.00"), 1, "A1", "VIP"),
            new TicketReservationDto(new BigDecimal("50.00"), 2, "A2", "VIP")
        );
        return new BeginReservationEvent(UUID.randomUUID(), reservationId, UUID.randomUUID(), tickets);
    }
}
