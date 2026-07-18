package org.ticketsouq.paymentservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.ticketsouq.sharedmodule.GeneralExceptions.ErrorResponse;

@Slf4j
@RestControllerAdvice
public class PaymentExceptionHandler {

    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<ErrorResponse> handlePaymentException(PaymentException ex) {
        HttpStatus status = ex.getStatus();
        log.error("Payment error: {}", ex.getMessage());
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status.value(), "Payment Error", ex.getMessage()));
    }
}
