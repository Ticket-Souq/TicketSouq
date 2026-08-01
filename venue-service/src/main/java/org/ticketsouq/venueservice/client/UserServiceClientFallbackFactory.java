package org.ticketsouq.venueservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import org.ticketsouq.sharedmodule.GeneralExceptions.RemoteServiceUnavailableException;

import java.util.UUID;

@Slf4j
@Component
public class UserServiceClientFallbackFactory implements FallbackFactory<UserServiceClient> {

    @Override
    public UserServiceClient create(Throwable cause) {
        log.error("user-service call failed: {}", cause.getMessage());
        return new UserServiceClient() {
            @Override
            public String getOrganizationNameByUserId(UUID userId) {
                throw new RemoteServiceUnavailableException("user-service", "organization lookup");
            }
        };
    }
}
