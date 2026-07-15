package org.ticketsouq.apigateway.config;

import org.springframework.http.HttpMethod;

import java.util.List;

public record SecurityRule(
    List<String> patterns,
    HttpMethod method,
    Access access,
    List<String> roles
) {
    public SecurityRule(List<String> patterns, Access access, List<String> roles) {
        this(patterns, null, access, roles);
    }

    public enum Access {
        PERMIT_ALL, DENY_ALL, HAS_ROLE, HAS_ANY_ROLE, AUTHENTICATED
    }
}
