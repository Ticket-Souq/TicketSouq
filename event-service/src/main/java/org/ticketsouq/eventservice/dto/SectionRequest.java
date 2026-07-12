package org.ticketsouq.eventservice.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record SectionRequest(
        @NotBlank String name,
        Integer capacity,
        Integer remainingCapacity,
        String color,
        BigDecimal price
) {}
