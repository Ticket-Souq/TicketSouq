package org.ticketsouq.paymentservice.dto;

import org.ticketsouq.paymentservice.enums.PaymentStatus;

import java.util.UUID;

public record PaymentResponse(String clientSecret, UUID paymentID, PaymentStatus paymentStatus, String msg) {
}
