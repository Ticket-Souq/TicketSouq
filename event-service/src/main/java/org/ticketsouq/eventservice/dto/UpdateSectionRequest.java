package org.ticketsouq.eventservice.dto;

import jakarta.validation.constraints.Positive;
import lombok.Builder;
import org.ticketsouq.sharedmodule.Validation.NullOrNotBlank;

import java.math.BigDecimal;

@Builder
public record UpdateSectionRequest(

    @NullOrNotBlank
    String name,

    @Positive
    BigDecimal price,

    @Positive
    Integer capacity

) {}
