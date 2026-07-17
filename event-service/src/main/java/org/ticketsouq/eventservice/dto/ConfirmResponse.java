package org.ticketsouq.eventservice.dto;

public record ConfirmResponse(
    String status
) {
    public static final ConfirmResponse CONFIRMED = new ConfirmResponse("CONFIRMED");
}
