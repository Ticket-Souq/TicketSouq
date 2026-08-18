package org.ticketsouq.paymentservice.paymentProviders;


import org.ticketsouq.paymentservice.dto.PaymentRequest;
import org.ticketsouq.paymentservice.dto.PaymentResponse;

import java.util.UUID;

public interface PaymentProvider {
    PaymentResponse pay(PaymentRequest request);

    PaymentResponse getPayment(UUID paymentId);

    void refund(UUID paymentId);
}
