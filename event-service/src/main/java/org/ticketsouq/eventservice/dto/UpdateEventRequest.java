package org.ticketsouq.eventservice.dto;

import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record UpdateEventRequest(
    @Valid List<UpdateSectionRequest> updateSectionRequests
) {

    public record UpdateSectionRequest(
        UUID id,
        BigDecimal price,
        Integer capacity
    ) {
    }
}
