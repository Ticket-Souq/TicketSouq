package org.ticketsouq.eventservice.dto;

import jakarta.validation.constraints.Positive;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record UpdateSectionRequest(

    String name,

    @Positive
    BigDecimal price,

    @Positive
    Integer capacity

) {}
