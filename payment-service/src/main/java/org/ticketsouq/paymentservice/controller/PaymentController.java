package org.ticketsouq.paymentservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ticketsouq.paymentservice.dto.PaymentResponse;
import org.ticketsouq.paymentservice.model.PaymentModel;
import org.ticketsouq.paymentservice.repository.PaymentRepository;
import org.ticketsouq.sharedmodule.GeneralExceptions.ResourceNotFoundException;

import java.util.UUID;

@RestController
@RequestMapping("api/v1/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentRepository paymentRepository;

    @GetMapping("/reservation/{reservationId}")
    public ResponseEntity<PaymentResponse> getPaymentByReservation(@PathVariable UUID reservationId) {
        PaymentModel payment = paymentRepository.findByReservationID(reservationId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment for reservation", reservationId));

        return ResponseEntity.ok(toResponse(payment));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPaymentDetails(@PathVariable UUID paymentId) {
        PaymentModel payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        return ResponseEntity.ok(toResponse(payment));
    }

    private PaymentResponse toResponse(PaymentModel payment) {
        return new PaymentResponse(
            payment.getClientSecret(),
            payment.getId(),
            payment.getPaymentStatus(),
            "Payment retrieved successfully"
        );
    }
}