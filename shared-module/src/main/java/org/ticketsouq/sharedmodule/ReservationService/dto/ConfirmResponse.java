package org.ticketsouq.sharedmodule.ReservationService.dto;

public record ConfirmResponse(
    String status
) {
    public static final ConfirmResponse CONFIRMED = new ConfirmResponse("CONFIRMED");
}
