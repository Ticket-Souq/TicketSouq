package org.ticketsouq.eventservice.dto;

import java.util.UUID;

public record ZoneStatusResponse(
    UUID zoneId,
    String name,
    Integer capacity,
    Integer booked,
    Integer locked,
    Integer available
) {}
