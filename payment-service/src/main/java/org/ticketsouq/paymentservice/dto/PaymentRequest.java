package org.ticketsouq.paymentservice.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentRequest(UUID reservationID , UUID customerID, UUID eventID , BigDecimal amount , String currency) {
}
