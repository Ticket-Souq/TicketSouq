package org.ticketsouq.sharedmodule.PaymentService.dto;

import java.util.UUID;

public record SagaPaymentResponse(
    UUID paymentID,
    String paymentStatus,
    String msg
) {}
