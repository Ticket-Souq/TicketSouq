package org.ticketsouq.notificationservice.exception;

public class EmailJobSerializationException extends RuntimeException {

    public EmailJobSerializationException(Throwable cause) {
        super("Failed to serialize email variables.", cause);
    }

}
