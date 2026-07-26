package org.ticketsouq.userservice.dto;

import java.util.UUID;

public record MemberSummaryResponse(
    UUID id,
    String email
) {}
