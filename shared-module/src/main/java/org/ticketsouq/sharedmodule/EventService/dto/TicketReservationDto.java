package org.ticketsouq.sharedmodule.EventService.dto;

import java.math.BigDecimal;

public record TicketReservationDto(
    BigDecimal price,
    Integer row,
    String label,
    String sectionName
) {}
