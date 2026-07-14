package org.ticketsouq.eventservice.dto.FrontendMap;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EventLayoutResponse(
    UUID id,
    String mode,
    String name,
    String description,
    UUID venueTemplateId,
    String organization,
    String status,
    String categoryName,
    String posterUrl,
    Instant startDate,
    Instant finishDate,
    List<RowResponse> rows,
    List<CategoryResponse> categories
) {
    public record CategoryResponse(
        UUID id,
        String name,
        String color,
        Integer capacity,
        Integer remainingCapacity,
        BigDecimal price
    ) {}

    public record RowResponse(
        String label,
        boolean aisle,
        List<CellResponse> cells
    ) {}

    public record CellResponse(
        UUID id,
        String type,
        String number,
        String status,
        UUID categoryId
    ) {}

}
