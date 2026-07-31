package org.ticketsouq.sharedmodule.EventService.dto;

import java.util.UUID;

public record ReservationTicketDto(
    UUID seatId,
    UUID sectionId,
    String holderName,
    String label
) {}
