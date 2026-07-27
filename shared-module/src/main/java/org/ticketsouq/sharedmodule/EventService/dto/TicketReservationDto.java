package org.ticketsouq.sharedmodule.EventService.dto;

import java.math.BigDecimal;

public record TicketReservationDto(
    BigDecimal price,
    String label,
    String sectionName
) {}
