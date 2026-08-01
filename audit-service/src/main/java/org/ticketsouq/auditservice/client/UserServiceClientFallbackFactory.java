package org.ticketsouq.auditservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import org.ticketsouq.sharedmodule.UserService.dto.UserEmail;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class UserServiceClientFallbackFactory implements FallbackFactory<userServiceClient> {

    @Override
    public userServiceClient create(Throwable cause) {
        log.error("user-service call failed: {}", cause.getMessage());
        return new userServiceClient() {
            @Override
            public List<UserEmail> getUsersEmails(List<UUID> ids) {
                log.warn("user-service unavailable, returning empty emails for ids={}", ids);
                return List.of();
            }
        };
    }
}
