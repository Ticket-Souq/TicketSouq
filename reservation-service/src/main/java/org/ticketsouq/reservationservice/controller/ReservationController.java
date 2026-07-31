package org.ticketsouq.reservationservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ticketsouq.reservationservice.dto.ReservationResponse;
import org.ticketsouq.reservationservice.service.ReservationService;
import org.ticketsouq.sharedmodule.GeneralExceptions.ResourceNotFoundException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reservation")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @GetMapping
    public ResponseEntity<List<ReservationResponse>> getMyReservations(@RequestHeader("X-User-Id") UUID userId) {
        List<ReservationResponse> responses = reservationService.getReservationsByUser(userId);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{reservationId}")
    public ResponseEntity<ReservationResponse> getReservation(
        @PathVariable UUID reservationId,
        @RequestHeader("X-User-Id") UUID userId) {
        ReservationResponse response = reservationService.getReservation(reservationId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Reservation", reservationId));
        return ResponseEntity.ok(response);
    }
}
