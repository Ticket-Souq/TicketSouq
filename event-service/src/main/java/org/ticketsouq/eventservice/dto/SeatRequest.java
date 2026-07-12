package org.ticketsouq.eventservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.ticketsouq.eventservice.model.enums.SeatStatus;

import java.math.BigDecimal;

public record SeatRequest(
        @NotNull Integer row,
        @NotNull Integer col,
        @NotBlank String lable,
        @NotNull SeatStatus status,
        BigDecimal price
) {}
