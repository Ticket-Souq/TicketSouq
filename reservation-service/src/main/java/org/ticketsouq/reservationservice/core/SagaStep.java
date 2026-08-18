package org.ticketsouq.reservationservice.core;

public enum SagaStep {
    INITIATED,
    PAYMENT,
    TICKET_ISSUANCE,
    LOCK_CONFIRMATION,
    COMPLETED,
    FAILED
}
