package org.ticketsouq.sharedmodule.EventService.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record LockSeatsRequest(
    @NotEmpty @Size(max = 10, message = "A maximum of 10 seats can be reserved") List<UUID> seatIds
) {
}
