package org.ticketsouq.paymentservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

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
                log.warn("user-service unavailable, returning null organization name for userId={}", userId);
                return null;
            }
        };
    }
}
