package org.ticketsouq.eventservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateSectionRequest(

    @NotBlank
    String name,

    @NotNull
    @Positive
    Integer capacity,

    @NotNull
    @Positive
    BigDecimal price

) {
}
