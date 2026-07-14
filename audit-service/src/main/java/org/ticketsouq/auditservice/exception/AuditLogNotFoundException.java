package org.ticketsouq.auditservice.exception;

import org.ticketsouq.sharedmodule.GeneralExceptions.ResourceNotFoundException;

public class AuditLogNotFoundException extends ResourceNotFoundException {

    public AuditLogNotFoundException(Object id) {
        super("AuditLog", id);
    }
}
