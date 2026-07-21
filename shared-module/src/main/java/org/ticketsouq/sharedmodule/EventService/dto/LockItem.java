package org.ticketsouq.sharedmodule.EventService.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record LockItem(
    UUID seatId,
    String row,
    String number,
    String section,
    BigDecimal price,
    int quantity
) {}
