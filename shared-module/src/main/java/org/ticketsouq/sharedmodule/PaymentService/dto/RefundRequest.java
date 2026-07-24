package org.ticketsouq.sharedmodule.PaymentService.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RefundRequest(
    @NotNull UUID reservationId,
    @NotNull UUID paymentId,
    String reason
) {}
