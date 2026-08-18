package org.ticketsouq.sharedmodule.ApiGateway.exception;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(UUID id) {
        super("User not found with id: " + id);
    }

    public UserNotFoundException(String email) {
        super("User not found with Email: " + email);
    }

}
