package org.ticketsouq.reservationservice.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

//saga working memory — serialized into the saga's payload

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationContext {
    private UUID reservationId;
    private UUID customerId;
    private UUID eventId;
    private List<UUID> seatIds;
    private UUID zoneId;
    private Integer quantity;
    private BigDecimal totalAmount;

    private String lockExpiresAt;
    private UUID paymentId;
    private UUID ticketId;
    private String qrCode;

    private String failedStep;
    private String failureReason;
}
