package org.ticketsouq.sharedmodule.ApiGateway.exception;

public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String Email) {
        super("The email already exists: " + Email);
    }
}
