package org.ticketsouq.reservationservice.exception;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.ticketsouq.sharedmodule.GeneralExceptions.BusinessException;
import org.ticketsouq.sharedmodule.GeneralExceptions.ErrorResponse;
import org.ticketsouq.sharedmodule.GeneralExceptions.ResourceNotFoundException;

@Slf4j
@RestControllerAdvice
public class ReservationExceptionHandler {

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ErrorResponse> handleFeign(FeignException ex) {
        log.warn("Event Service call failed: status={}, message={}", ex.status(), ex.getMessage());
        HttpStatus status = ex.status() > 0
            ? HttpStatus.valueOf(ex.status())
            : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status)
            .body(ErrorResponse.of(status.value(), "Event Service Error", ex.getMessage()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        HttpStatus status = ex.getStatus();
        return ResponseEntity.status(status)
            .body(ErrorResponse.of(status.value(), status.getReasonPhrase(), ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of(404, "Not Found", ex.getMessage()));
    }
}
