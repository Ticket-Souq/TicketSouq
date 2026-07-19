package org.ticketsouq.eventservice.dto;

public record ReleaseResponse(
    String status
) {
    public static final ReleaseResponse RELEASED = new ReleaseResponse("RELEASED");
}
