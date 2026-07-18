package org.ticketsouq.paymentservice.paymentProviders;

import org.ticketsouq.paymentservice.dto.PaymentRequest;
import org.ticketsouq.paymentservice.dto.PaymentResponse;
import org.ticketsouq.paymentservice.enums.PaymentStatus;
import org.ticketsouq.paymentservice.model.PaymentModel;
import org.ticketsouq.sharedmodule.GeneralExceptions.ResourceNotFoundException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MockPaymentProvider implements PaymentProvider {

    private final Map<UUID, PaymentModel> paymentStore = new ConcurrentHashMap<>();

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
                .currency(request.currency())
                .paymentStatus(status)
                .transactionRef(UUID.randomUUID().toString())
                .build();

        paymentStore.put(payment.getId(), payment);

        String msg = status == PaymentStatus.SUCCESS
                ? "Payment has been completed"
                : "Payment Failed";

        return new PaymentResponse(null, payment.getId(), payment.getPaymentStatus(), msg);
    }

    @Override
    public PaymentResponse getPayment(UUID paymentId) {
        PaymentModel payment = paymentStore.get(paymentId);
        if (payment == null) {
            throw new ResourceNotFoundException("Payment", paymentId);
        }
        return new PaymentResponse(null, payment.getId(), payment.getPaymentStatus(), payment.getTransactionRef());
    }

    @Override
    public void refund(UUID paymentId) {
        PaymentModel payment = paymentStore.get(paymentId);
        if (payment == null) {
            throw new ResourceNotFoundException("Payment", paymentId);
        }
        payment.setPaymentStatus(PaymentStatus.REFUNDED);
        payment.setUpdatedAt(Instant.now());
    }
}
