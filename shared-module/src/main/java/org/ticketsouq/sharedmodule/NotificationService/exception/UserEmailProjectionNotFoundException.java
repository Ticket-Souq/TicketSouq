package org.ticketsouq.sharedmodule.NotificationService.exception;

import java.util.UUID;

public class UserEmailProjectionNotFoundException extends RuntimeException {

    public UserEmailProjectionNotFoundException(UUID userId) {
        super("Email projection not found for user " + userId);
    }

}
