package org.ticketsouq.sharedmodule.ApiGateway.exception;

import org.ticketsouq.sharedmodule.GeneralExceptions.ConflictException;

public class EmailAlreadyExistsException extends ConflictException {
    public EmailAlreadyExistsException(String email) {
        super("The email already exists: " + email);
    }
}
