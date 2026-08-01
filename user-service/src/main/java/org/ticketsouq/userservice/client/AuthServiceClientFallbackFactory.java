package org.ticketsouq.userservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.ticketsouq.sharedmodule.GeneralExceptions.RemoteServiceUnavailableException;

import java.util.UUID;

@Slf4j
@Component
public class AuthServiceClientFallbackFactory implements FallbackFactory<AuthServiceClient> {

    @Override
    public AuthServiceClient create(Throwable cause) {
        log.error("api-gateway call failed: {}", cause.getMessage());
        return new AuthServiceClient() {
            @Override
            public ResponseEntity<Void> unlockOrg(UUID orgHeadId) {
                throw new RemoteServiceUnavailableException("api-gateway", "org unlock");
            }
        };
    }
}
