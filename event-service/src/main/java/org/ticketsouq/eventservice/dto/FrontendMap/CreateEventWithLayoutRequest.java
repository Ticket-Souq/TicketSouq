package org.ticketsouq.eventservice.dto.FrontendMap;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateEventWithLayoutRequest(
    String mode,
    String name,
    String description,
    UUID id,
    String categoryName,
    String posterUrl,
    Instant startDate,
    Instant finishDate,
    List<RowDto> rows,
    List<CategoryDto> categories
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

    @Builder
    public record RowDto(
        String id,
        String label,
        Boolean aisle,
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
}
