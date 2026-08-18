package org.ticketsouq.sharedmodule.ApiGateway.exception;

import org.springframework.http.HttpStatus;
import org.ticketsouq.sharedmodule.GeneralExceptions.BusinessException;

public class RateLimitExceededException extends BusinessException {
    public RateLimitExceededException() {
        super("Rate limit exceeded", HttpStatus.TOO_MANY_REQUESTS);
    }
}
