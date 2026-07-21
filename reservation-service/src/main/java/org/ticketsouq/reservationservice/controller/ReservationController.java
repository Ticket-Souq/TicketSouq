package org.ticketsouq.reservationservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.ticketsouq.reservationservice.dto.CheckoutRequest;
import org.ticketsouq.reservationservice.dto.CheckoutResponse;
import org.ticketsouq.reservationservice.dto.ReservationRequest;
import org.ticketsouq.reservationservice.dto.ReservationResponse;
import org.ticketsouq.reservationservice.service.ReservationService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> checkout(
        @RequestHeader("X-User-Id") UUID customerId,
        @Valid @RequestBody CheckoutRequest request,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            CheckoutResponse cached = reservationService.getIdempotentResponse(idempotencyKey);
            if (cached != null) {
                return ResponseEntity.ok(cached);
            }
        }

        CheckoutResponse response = reservationService.createCheckout(customerId, request);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            reservationService.cacheIdempotentResponse(idempotencyKey, response);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReservationResponse> getReservation(@PathVariable UUID id) {
        ReservationResponse response = reservationService.getReservation(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<ReservationResponse>> getMyReservations(@RequestHeader("X-User-Id") UUID customerId) {
        List<ReservationResponse> responses = reservationService.getReservationsByCustomer(customerId);
        return ResponseEntity.ok(responses);
    }
}
