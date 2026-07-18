package org.ticketsouq.paymentservice.exception;

import org.springframework.http.HttpStatus;
import org.ticketsouq.sharedmodule.GeneralExceptions.BusinessException;

public class PaymentException extends BusinessException {

    public PaymentException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }

    public PaymentException(String message, Throwable cause) {
        super(message + ": " + cause.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
