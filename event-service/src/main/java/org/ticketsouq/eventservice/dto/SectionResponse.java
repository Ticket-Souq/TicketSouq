package org.ticketsouq.eventservice.dto;

import org.ticketsouq.eventservice.model.Section;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record SectionResponse(
        UUID id,
        UUID eventId,
        String name,
        Integer capacity,
        Integer remainingCapacity,
        String color,
        BigDecimal price,
        LocalDateTime updatedAt
) {
    public static SectionResponse from(Section section) {
        return new SectionResponse(
                section.getId(),
                section.getEvent().getId(),
                section.getName(),
                section.getCapacity(),
                section.getRemainingCapacity(),
                section.getColor(),
                section.getPrice(),
                section.getUpdatedAt()
        );
    }
}
