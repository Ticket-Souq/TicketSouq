package org.ticketsouq.paymentservice.paymentProviders;

import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.ticketsouq.paymentservice.dto.PaymentRequest;
import org.ticketsouq.paymentservice.dto.PaymentResponse;
import org.ticketsouq.paymentservice.enums.PaymentProviderEnum;
import org.ticketsouq.paymentservice.enums.PaymentStatus;
import org.ticketsouq.paymentservice.model.PaymentModel;
import org.ticketsouq.paymentservice.repository.PaymentRepository;

import org.ticketsouq.sharedmodule.GeneralExceptions.ResourceNotFoundException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@Profile("mock")
@AllArgsConstructor
public class MockPaymentProvider implements PaymentProvider {

    private final PaymentRepository paymentRepository;

    @Override
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
                .paymentProvider(PaymentProviderEnum.MOCK_PAYMENT)
                .transactionRef(UUID.randomUUID().toString())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        paymentRepository.save(payment);

        String msg = status == PaymentStatus.SUCCESS
                ? "Payment has been completed"
                : "Payment Failed";

        return new PaymentResponse(payment.getId(), payment.getPaymentStatus(), msg);
    }

    @Override
    public PaymentResponse getPayment(UUID paymentId) {
        PaymentModel payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
        return new PaymentResponse(payment.getId(), payment.getPaymentStatus(), payment.getTransactionRef());
    }

    @Override
    public void refund(UUID paymentId) {
        PaymentModel payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
        payment.setPaymentStatus(PaymentStatus.REFUNDED);
        payment.setUpdatedAt(Instant.now());
        paymentRepository.save(payment);
    }
}
