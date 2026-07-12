package org.ticketsouq.eventservice.dto;

import org.ticketsouq.eventservice.model.Seat;
import org.ticketsouq.eventservice.model.enums.SeatStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record SeatResponse(
        UUID id,
        UUID sectionId,
        Integer row,
        Integer col,
        String lable,
        SeatStatus status,
        BigDecimal price,
        LocalDateTime updatedAt
) {
    public static SeatResponse from(Seat seat) {
        BigDecimal effectivePrice = seat.getPrice() != null
                ? seat.getPrice()
                : seat.getSection().getPrice();
        return new SeatResponse(
                seat.getId(),
                seat.getSection().getId(),
                seat.getRow(),
                seat.getCol(),
                seat.getLable(),
                seat.getStatus(),
                effectivePrice,
                seat.getUpdatedAt()
        );
    }
}
