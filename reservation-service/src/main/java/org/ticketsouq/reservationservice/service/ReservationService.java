package org.ticketsouq.reservationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.reservationservice.dto.ReservationContext;
import org.ticketsouq.reservationservice.dto.ReservationResponse;
import org.ticketsouq.reservationservice.mapper.ReservationMapper;
import org.ticketsouq.reservationservice.model.Reservation;
import org.ticketsouq.reservationservice.repository.ReservationRepository;
import org.ticketsouq.sharedmodule.EventService.events.BeginReservationEvent;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ReservationMapper reservationMapper;

    @Transactional
    public Reservation createReservation(BeginReservationEvent event) {
        if (reservationRepository.existsById(event.reservationId())) {
            return null;
        }

        Reservation reservation = reservationMapper.createReservation(event);
        return reservationRepository.save(reservation);
    }

    public ReservationContext createReservationContext(Reservation reservation, BeginReservationEvent event) {
        return reservationMapper.createReservationContext(reservation, event);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getReservationsByUser(UUID userId) {
        return reservationRepository.findByUserId(userId)
            .stream()
            .map(reservationMapper::toResponse)
            .toList();
    }
}
