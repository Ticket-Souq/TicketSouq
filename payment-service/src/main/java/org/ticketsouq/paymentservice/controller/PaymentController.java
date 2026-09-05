package org.ticketsouq.paymentservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ticketsouq.paymentservice.dto.PaymentResponse;
import org.ticketsouq.paymentservice.service.PaymentService;

import java.nio.file.AccessDeniedException;
import java.util.UUID;

@RestController
@RequestMapping("api/v1/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/reservation/{reservationId}")
    public ResponseEntity<PaymentResponse> getPaymentByReservation(
            @PathVariable UUID reservationId,
            @RequestHeader("X-User-Id") UUID userId) throws AccessDeniedException {
        return ResponseEntity.ok(paymentService.getPaymentByReservation(reservationId, userId));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPaymentDetails(
            @PathVariable UUID paymentId,
            @RequestHeader("X-User-Id") UUID userId) throws AccessDeniedException {
        return ResponseEntity.ok(paymentService.getPaymentById(paymentId, userId));
    }
}
