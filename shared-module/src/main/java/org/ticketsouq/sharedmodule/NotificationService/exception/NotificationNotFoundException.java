package org.ticketsouq.sharedmodule.NotificationService.exception;

public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException(Long id) {
        super("Notification with id " + id + " was not found.");
    }
}
