package org.ticketsouq.sharedmodule.NotificationService.exception;

import org.ticketsouq.sharedmodule.GeneralExceptions.ResourceNotFoundException;

public class NotificationNotFoundException extends ResourceNotFoundException {
    public NotificationNotFoundException(Long id) {
        super("Notification", id);
    }
}
