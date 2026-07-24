package org.ticketsouq.sharedmodule.TicketService.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TicketData(
    UUID ticketId,
    String qrCode,
    String sectionName,
    Integer row,
    String label,
    BigDecimal price
) {}
