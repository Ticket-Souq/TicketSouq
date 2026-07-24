package org.ticketsouq.paymentservice.paymentProviders;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.paymentservice.dto.PaymentRequest;
import org.ticketsouq.paymentservice.dto.PaymentResponse;
import org.ticketsouq.paymentservice.enums.PaymentStatus;
import org.ticketsouq.paymentservice.model.PaymentModel;
import org.ticketsouq.paymentservice.repository.PaymentRepository;
import org.ticketsouq.sharedmodule.GeneralExceptions.ResourceNotFoundException;

import java.math.BigDecimal;
import java.util.UUID;

@RequiredArgsConstructor
public class MockPaymentProvider implements PaymentProvider {

    private final PaymentRepository paymentRepository;

    @Override
    @Transactional
    public PaymentResponse pay(PaymentRequest request) {
        PaymentStatus status;
        if (request.amount().compareTo(BigDecimal.valueOf(1000)) > 0) {
            status = PaymentStatus.SUCCESS;
        } else {
            status = PaymentStatus.FAILED;
        }

        PaymentModel payment = PaymentModel.builder()
                .reservationID(request.reservationID())
                .customerID(request.customerID())
                .amount(request.amount())
                .paymentStatus(status)
                .transactionRef(UUID.randomUUID().toString())
                .build();

        paymentRepository.save(payment);

        String msg = status == PaymentStatus.SUCCESS
                ? "Payment has been completed"
                : "Payment Failed";

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
            throw new org.ticketsouq.paymentservice.exception.PaymentException(
                "Cannot refund a payment that is not in SUCCESS status. Current status: " + payment.getPaymentStatus());
        }

        payment.setPaymentStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(payment);
    }
}
