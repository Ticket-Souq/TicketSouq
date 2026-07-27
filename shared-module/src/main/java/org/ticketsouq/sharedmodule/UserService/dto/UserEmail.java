package org.ticketsouq.sharedmodule.UserService.dto;

import java.util.UUID;

public record UserEmail(
    UUID id,
    String email
) {}
