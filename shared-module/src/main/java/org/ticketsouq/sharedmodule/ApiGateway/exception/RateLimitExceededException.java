package org.ticketsouq.sharedmodule.ApiGateway.exception;

public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException() {
        super("Rate limit exceeded");
    }

    public RateLimitExceededException(String message) {
        super(message);
    }
}
