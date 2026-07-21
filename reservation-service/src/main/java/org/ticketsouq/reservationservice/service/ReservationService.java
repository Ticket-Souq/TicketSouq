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

import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.RESERVATION_CREATED;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ReservationMapper reservationMapper;

    @Transactional
    public void createReservation(BeginReservationEvent event) {
        Reservation reservation = reservationMapper.createReservation(event);
        reservation = reservationRepository.save(reservation);
        ReservationContext context = reservationMapper.createReservationContext(reservation);
        sagaOrchestrator.startSaga(context, reservation.getId().toString());
        sagaEventPublisher.publishAfterCommit(RESERVATION_CREATED, reservation.getId().toString(), reservationMapper.toReservationCreatedEvent(reservation));
        reservationMapper.toResponse(reservation);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getReservationsByCustomer(UUID customerId) {
        return reservationRepository.findByCustomerId(customerId)
            .stream()
            .map(reservationMapper::toResponse)
            .toList();
    }


}
