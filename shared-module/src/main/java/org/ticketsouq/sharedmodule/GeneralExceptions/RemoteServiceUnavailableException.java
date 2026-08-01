package org.ticketsouq.sharedmodule.GeneralExceptions;

import org.springframework.http.HttpStatus;

public class RemoteServiceUnavailableException extends BusinessException {

    public RemoteServiceUnavailableException(String serviceName) {
        super(serviceName + " is unavailable", HttpStatus.SERVICE_UNAVAILABLE);
    }

    public RemoteServiceUnavailableException(String serviceName, String operation) {
        super(serviceName + " is unavailable, " + operation + " cannot be completed", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
