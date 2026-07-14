package org.ticketsouq.paymentservice.dto;

import org.ticketsouq.paymentservice.enums.PaymentStatus;

import java.util.UUID;

public record GetPaymentResponse(UUID paymentID, PaymentStatus paymentStatus, String transactionRef) {
}
