package org.ticketsouq.paymentservice.dto;

import lombok.NoArgsConstructor;
import org.ticketsouq.paymentservice.enums.PaymentStatus;

import java.util.UUID;


public record PaymentResponse(UUID paymentID , PaymentStatus paymentStatus , String msg) {
}
