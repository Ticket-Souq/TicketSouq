package org.ticketsouq.sharedmodule.ReservationService.dto;

public record ReleaseResponse(
    String status
) {
    public static final ReleaseResponse RELEASED = new ReleaseResponse("RELEASED");
}
