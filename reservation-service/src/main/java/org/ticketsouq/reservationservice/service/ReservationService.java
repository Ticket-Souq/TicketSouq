package org.ticketsouq.reservationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.reservationservice.dto.ReservationRequest;
import org.ticketsouq.reservationservice.dto.ReservationResponse;
import org.ticketsouq.reservationservice.model.Reservation;
import org.ticketsouq.reservationservice.repository.ReservationRepository;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;

    @Transactional
    public ReservationResponse createReservation(UUID customerId, ReservationRequest request) {
        // TODO Implement
        throw new RuntimeException("Not Supported yet");
    }

    public ReservationResponse getReservation(UUID id) {
        Reservation reservation = reservationRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Reservation not found: " + id));
        return toResponse(reservation);
    }

    public List<ReservationResponse> getReservationsByCustomer(UUID customerId) {
        return reservationRepository.findByCustomerId(customerId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    private ReservationResponse toResponse(Reservation reservation) {
        return new ReservationResponse(
            reservation.getId(),
            reservation.getCustomerId(),
            reservation.getEventId(),
            reservation.getSeatIds(),
            reservation.getZoneId(),
            reservation.getQuantity(),
            reservation.getTotalAmount(),
            reservation.getStatus(),
            reservation.getPaymentId(),
            reservation.getTicketId(),
            reservation.getCreatedAt()
        );
    }
}
