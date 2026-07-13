package org.ticketsouq.eventservice.Mapper;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record EventFrontendDto(

    String id,
    String mode,          // mapped from BookingModel enum
    String name,          // mapped from Event.title

    List<RowDto> rows,
    StageDto stage,
    List<CategoryDto> categories,   // mapped from Section list
    List<VerticalAisleDto> verticalAisles
) {


    @Builder
    public record CategoryDto(
        String id,
        String name,
        String color,
        Integer capacity,
        BigDecimal price
    ) {
    }

    public record RowDto(
        String id,
        String label,           // e.g. "Row 1"
        boolean aisle,          // no backend equivalent; always false unless extended
        List<CellDto> cells
    ) {
    }

    @Builder
    public record CellDto(
        String id,
        String type,
        String number,
        String status,
        String categoryId
    ) {
    }

    @Builder
    public record StageDto(
        String color,
        String label,
        String position    // e.g. "top", "bottom"
    ) {
    }

    @Builder
    public record VerticalAisleDto(
        String id,
        Object endRowId,        // nullable in frontend contract
        Object startRowId,      // nullable in frontend contract
        int columnIndex
    ) {
    }
}
