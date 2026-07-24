package org.ticketsouq.reservationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.ticketsouq.sharedmodule.EventService.dto.TicketReservationDto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class ReservationContext {
    private UUID reservationId;
    private UUID userId;
    private UUID eventId;
    private List<TicketReservationDto> tickets;
    private BigDecimal totalAmount;
    private UUID paymentId;
}
