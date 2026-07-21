package org.ticketsouq.sharedmodule.EventService.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record LockSeatsResponse(
    String status,
    LocalDateTime expiresAt,
    List<LockItem> items,
    BigDecimal totalPrice
) {}
