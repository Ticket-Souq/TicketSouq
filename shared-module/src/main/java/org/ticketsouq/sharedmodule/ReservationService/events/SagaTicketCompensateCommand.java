package org.ticketsouq.sharedmodule.ReservationService.events;

import java.util.UUID;

public record SagaTicketCompensateCommand(
    UUID reservationId
) {
}