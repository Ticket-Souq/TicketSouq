package org.ticketsouq.sharedmodule.TicketService.dto;

import java.util.List;

public record CreateTicketResponse(
    List<TicketData> tickets
) {}
