package org.ticketsouq.sharedmodule.EventService.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.ticketsouq.sharedmodule.GeneralExceptions.ErrorResponse;
import org.ticketsouq.sharedmodule.ReservationService.exception.ReservationExpiredException;

@Slf4j
@RestControllerAdvice
public class LockExceptionHandler {

    @ExceptionHandler(InvalidEventTypeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidEventType(InvalidEventTypeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.of(400, "Invalid Event Type", ex.getMessage()));
    }

    @ExceptionHandler(SeatNotInEventException.class)
    public ResponseEntity<ErrorResponse> handleSeatNotInEvent(SeatNotInEventException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.of(400, "Seat Not Found In Event", ex.getMessage()));
    }

    @ExceptionHandler(SeatAlreadyBookedException.class)
    public ResponseEntity<ErrorResponse> handleSeatAlreadyBooked(SeatAlreadyBookedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse.of(409, "Seat Already Booked", ex.getMessage()));
    }

    @ExceptionHandler(SeatAlreadyLockedException.class)
    public ResponseEntity<ErrorResponse> handleSeatAlreadyLocked(SeatAlreadyLockedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse.of(409, "Seat Already Locked", ex.getMessage()));
    }

    @ExceptionHandler(ZoneCapacityExceededException.class)
    public ResponseEntity<ErrorResponse> handleZoneCapacityExceeded(ZoneCapacityExceededException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.of(400, "The Required Capacity Not Available", ex.getMessage()));
    }

    @ExceptionHandler(ReservationExpiredException.class)
    public ResponseEntity<ErrorResponse> handleReservationExpired(ReservationExpiredException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.of(400, "Reservation Expired", ex.getMessage()));
    }

    @ExceptionHandler(LockExpiredException.class)
    public ResponseEntity<ErrorResponse> handleLockExpired(LockExpiredException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse.of(409, "Lock Expired", ex.getMessage()));
    }

}
