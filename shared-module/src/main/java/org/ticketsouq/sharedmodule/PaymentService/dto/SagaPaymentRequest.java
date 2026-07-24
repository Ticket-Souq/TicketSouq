package org.ticketsouq.sharedmodule.PaymentService.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record SagaPaymentRequest(
    @NotNull UUID reservationID,
    @NotNull UUID customerID,
    @NotNull UUID eventID,
    @NotNull @Positive BigDecimal amount
) {}
