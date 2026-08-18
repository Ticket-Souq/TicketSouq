package org.ticketsouq.paymentservice.paymentProviders;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.paymentservice.dto.PaymentRequest;
import org.ticketsouq.paymentservice.dto.PaymentResponse;
import org.ticketsouq.paymentservice.enums.PaymentStatus;
import org.ticketsouq.paymentservice.model.PaymentModel;
import org.ticketsouq.paymentservice.repository.PaymentRepository;
import org.ticketsouq.sharedmodule.GeneralExceptions.ResourceNotFoundException;
import org.ticketsouq.sharedmodule.PaymentService.exception.PaymentException;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@RequiredArgsConstructor
public class MockPaymentProvider implements PaymentProvider {

    private final PaymentRepository paymentRepository;
    private final int successRate;

    @Override
    @Transactional
    public PaymentResponse pay(PaymentRequest request) {
        int rate = Math.max(0, Math.min(100, successRate));
        PaymentStatus status = ThreadLocalRandom.current().nextInt(100) < rate ? PaymentStatus.SUCCESS : PaymentStatus.FAILED;

        PaymentModel payment = PaymentModel.builder()
                .reservationID(request.reservationID())
                .customerID(request.customerID())
                .amount(request.amount())
                .paymentStatus(status)
                .transactionRef(UUID.randomUUID().toString())
                .build();

        paymentRepository.save(payment);

        String msg = status == PaymentStatus.SUCCESS ? "Payment has been completed" : "Payment Failed";

        return new PaymentResponse(null, payment.getId(), payment.getPaymentStatus(), msg);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID paymentId) {
        PaymentModel payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        return new PaymentResponse(null, payment.getId(), payment.getPaymentStatus(), payment.getTransactionRef());
    }

    @Override
    @Transactional
    public void refund(UUID paymentId) {
        PaymentModel payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        if (payment.getPaymentStatus() != PaymentStatus.SUCCESS) {
            throw new PaymentException(
                "Cannot refund a payment that is not in SUCCESS status. Current status: " + payment.getPaymentStatus());
        }

        payment.setPaymentStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(payment);
    }
}
