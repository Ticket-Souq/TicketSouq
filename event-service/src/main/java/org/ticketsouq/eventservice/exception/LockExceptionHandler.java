package org.ticketsouq.eventservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class LockExceptionHandler {

    @ExceptionHandler(EventNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleEventNotFound(EventNotFoundException ex) {
        return buildResponse(404, ex.getReasonCode(), ex.getMessage());
    }

    @ExceptionHandler(InvalidEventTypeException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidEventType(InvalidEventTypeException ex) {
        Map<String, Object> body = baseBody(400, ex.getReasonCode(), ex.getMessage());
        body.put("expected", ex.getExpected());
        return ResponseEntity.status(400).body(body);
    }

    @ExceptionHandler(SeatNotInEventException.class)
    public ResponseEntity<Map<String, Object>> handleSeatNotInEvent(SeatNotInEventException ex) {
        Map<String, Object> body = baseBody(400, ex.getReasonCode(), ex.getMessage());
        body.put("seatIds", ex.getSeatIds());
        return ResponseEntity.status(400).body(body);
    }

    @ExceptionHandler(SeatAlreadyBookedException.class)
    public ResponseEntity<Map<String, Object>> handleSeatAlreadyBooked(SeatAlreadyBookedException ex) {
        Map<String, Object> body = baseBody(409, ex.getReasonCode(), ex.getMessage());
        body.put("conflictingSeats", ex.getConflictingSeats());
        return ResponseEntity.status(409).body(body);
    }

    @ExceptionHandler(SeatAlreadyLockedException.class)
    public ResponseEntity<Map<String, Object>> handleSeatAlreadyLocked(SeatAlreadyLockedException ex) {
        Map<String, Object> body = baseBody(409, ex.getReasonCode(), ex.getMessage());
        body.put("conflictingSeats", ex.getConflictingSeats());
        return ResponseEntity.status(409).body(body);
    }

    @ExceptionHandler(ZoneCapacityExceededException.class)
    public ResponseEntity<Map<String, Object>> handleZoneCapacityExceeded(ZoneCapacityExceededException ex) {
        Map<String, Object> body = baseBody(400, ex.getReasonCode(), ex.getMessage());
        body.put("available", ex.getAvailable());
        return ResponseEntity.status(400).body(body);
    }

    @ExceptionHandler(ReservationExpiredException.class)
    public ResponseEntity<Map<String, Object>> handleReservationExpired(ReservationExpiredException ex) {
        return buildResponse(409, ex.getReasonCode(), ex.getMessage());
    }

    @ExceptionHandler(LockExpiredException.class)
    public ResponseEntity<Map<String, Object>> handleLockExpired(LockExpiredException ex) {
        return buildResponse(409, ex.getReasonCode(), ex.getMessage());
    }

    @ExceptionHandler(LockMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleLockMismatch(LockMismatchException ex) {
        return buildResponse(409, ex.getReasonCode(), ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> buildResponse(int status, String reason, String message) {
        return ResponseEntity.status(status).body(baseBody(status, reason, message));
    }

    private Map<String, Object> baseBody(int status, String reason, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "FAILED");
        body.put("reason", reason);
        body.put("message", message);
        body.put("timestamp", Instant.now());
        return body;
    }
}
