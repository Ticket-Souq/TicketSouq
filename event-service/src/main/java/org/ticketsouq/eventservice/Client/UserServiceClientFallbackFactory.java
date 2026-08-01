package org.ticketsouq.eventservice.Client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import org.ticketsouq.sharedmodule.GeneralExceptions.RemoteServiceUnavailableException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class UserServiceClientFallbackFactory implements FallbackFactory<UserServiceClient> {

    @Override
    public UserServiceClient create(Throwable cause) {
        log.error("user-service call failed: {}", cause.getMessage());
        return new UserServiceClient() {
            @Override
            public String getOrganizationName(UUID userId) {
                throw new RemoteServiceUnavailableException("user-service", "organization lookup");
            }

            @Override
            public Map<String, String> getUserNames(List<UUID> ids) {
                log.warn("user-service unavailable, returning empty names for ids={}", ids);
                return Map.of();
            }
        };
    }
}
