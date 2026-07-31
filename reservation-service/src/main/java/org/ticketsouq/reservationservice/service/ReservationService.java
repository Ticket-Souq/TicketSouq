package org.ticketsouq.reservationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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
        Reservation reservation = reservationMapper.createReservation(event);
        try {
            return reservationRepository.save(reservation);
        } catch (DataIntegrityViolationException e) {
            return reservationRepository.findById(event.reservationId()).orElse(null);
        }
    }

    @Transactional(readOnly = true)
    public ReservationContext createReservationContext(Reservation reservation, BeginReservationEvent event) {
        return reservationMapper.createReservationContext(reservation, event);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getReservationsByUser(UUID userId) {
        return reservationRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .stream()
            .map(reservationMapper::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public java.util.Optional<ReservationResponse> getReservation(UUID reservationId, UUID userId) {
        return reservationRepository.findById(reservationId)
            .filter(r -> r.getUserId().equals(userId))
            .map(reservationMapper::toResponse);
    }
}
