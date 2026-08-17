package org.ticketsouq.paymentservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.ticketsouq.sharedmodule.GeneralExceptions.ForbiddenException;
import org.ticketsouq.sharedmodule.GeneralExceptions.ResourceNotFoundException;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("api/v1/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentRepository paymentRepository;

    @GetMapping("/reservation/{reservationId}")
    public ResponseEntity<PaymentResponse> getPaymentByReservation(
            @PathVariable UUID reservationId,
            @RequestHeader("X-User-Id") UUID userId) {
        PaymentModel payment = paymentRepository.findByReservationID(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment for reservation", reservationId));
        assertOwned(payment, userId);
        return ResponseEntity.ok(toResponse(payment));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPaymentDetails(
            @PathVariable UUID paymentId,
            @RequestHeader("X-User-Id") UUID userId) {
        PaymentModel payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
        assertOwned(payment, userId);
        return ResponseEntity.ok(toResponse(payment));
    }

    private void assertOwned(PaymentModel payment, UUID userId) {
        if (payment.getCustomerID() != null && !payment.getCustomerID().equals(userId)) {
            log.warn("User {} attempted to access payment {} owned by {}",
                userId, payment.getId(), payment.getCustomerID());
            throw new ForbiddenException("You do not have access to this payment");
        }
    }

    private PaymentResponse toResponse(PaymentModel payment) {
        boolean exposeClientSecret = payment.getPaymentStatus() == PaymentStatus.PENDING;
        return new PaymentResponse(
                exposeClientSecret ? payment.getClientSecret() : null,
                payment.getId(),
                payment.getPaymentStatus(),
                "Payment retrieved successfully"
        );
    }
}
