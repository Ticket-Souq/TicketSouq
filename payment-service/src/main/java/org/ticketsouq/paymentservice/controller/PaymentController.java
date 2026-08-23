package org.ticketsouq.paymentservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ticketsouq.paymentservice.dto.PaymentResponse;
import org.ticketsouq.paymentservice.enums.PaymentStatus;
import org.ticketsouq.paymentservice.model.PaymentModel;
import org.ticketsouq.paymentservice.repository.PaymentRepository;
import org.ticketsouq.sharedmodule.GeneralExceptions.ResourceNotFoundException;

import java.nio.file.AccessDeniedException;
import java.util.UUID;

@RestController
@RequestMapping("api/v1/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentRepository paymentRepository;

    @GetMapping("/reservation/{reservationId}")
    public ResponseEntity<PaymentResponse> getPaymentByReservation(
            @PathVariable UUID reservationId,
            @RequestHeader("X-User-Id") UUID userId) throws AccessDeniedException {
        PaymentModel payment = paymentRepository.findByReservationID(reservationId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment for reservation", reservationId));

        if (!payment.getCustomerID().equals(userId)) {
            throw new AccessDeniedException("You are not allowed to view this payment");
        }

        return ResponseEntity.ok(toResponse(payment));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPaymentDetails(
            @PathVariable UUID paymentId,
            @RequestHeader("X-User-Id") UUID userId) throws AccessDeniedException {
        PaymentModel payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        if (!payment.getCustomerID().equals(userId)) {
            throw new AccessDeniedException("You are not allowed to view this payment");
        }

        return ResponseEntity.ok(toResponse(payment));
    }

    private PaymentResponse toResponse(PaymentModel payment) {
        // Only expose clientSecret while payment is still pending; otherwise hide it
        String secret = payment.getPaymentStatus() == PaymentStatus.PENDING
                ? payment.getClientSecret()
                : null;
        return new PaymentResponse(
            secret,
            payment.getId(),
            payment.getPaymentStatus(),
            "Payment retrieved successfully"
        );
    }
}