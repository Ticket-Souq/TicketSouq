package org.ticketsouq.outbox.kafka;

public class OutboxKafkaException extends RuntimeException {

    public OutboxKafkaException(String message, Throwable cause) {
        super(message, cause);
    }
}
