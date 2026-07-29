package org.ticketsouq.apigateway.client;

import feign.RetryableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import org.ticketsouq.apigateway.dto.OrgMemberResponse;
import org.ticketsouq.sharedmodule.ApiGateway.dto.CreateUserRequest;
import org.ticketsouq.sharedmodule.ApiGateway.dto.GenerateMembersRequest;

import java.net.ConnectException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class UserServiceClientFallbackFactory implements FallbackFactory<UserServiceClient> {

    private static String resolveReason(Throwable cause) {
        Throwable root = cause;
        while (root.getCause() != null) root = root.getCause();

        String msg = root.getMessage();
        if (msg == null) return "unknown error";

        if (msg.contains("does not contain an instance"))
            return "no instances available in service discovery — user-service may be down or not registered with Eureka";

        if (msg.contains("Connection refused"))
            return "connection refused — user-service is not accepting connections";

        if (root instanceof ConnectException)
            return "cannot connect to user-service — the service may be down";

        if (root instanceof RetryableException)
            return "request to user-service failed after retries — " + msg;

        if (msg.contains("timeout") || msg.contains("timed out") || msg.contains("TimeOut"))
            return "request to user-service timed out — the service may be overloaded";

        if (msg.contains("circuit-breaker") || msg.contains("CircuitBreaker") || msg.contains("circuit breaker"))
            return "circuit breaker is OPEN — fast-failing to prevent cascading failures";

        return msg;
    }

    @Override
    public UserServiceClient create(Throwable cause) {
        String reason = resolveReason(cause);
        log.error("user-service call failed: {}", reason);

        return new UserServiceClient() {
            @Override
            public void registerUser(CreateUserRequest request) {
                throw new RuntimeException("user-service is unavailable (%s), registration cannot be completed".formatted(reason), cause);
            }

            @Override
            public boolean isBelongToBannedOrg(UUID userId) {
                log.warn("user-service is unavailable (%s), assuming user %s is NOT banned".formatted(reason, userId));
                return false;
            }

            @Override
            public void generateMembers(GenerateMembersRequest request) {
                throw new RuntimeException("user-service is unavailable (%s), member generation cannot be completed".formatted(reason), cause);
            }

            @Override
            public List<OrgMemberResponse> getOrgMembers(UUID headUserId) {
                log.warn("user-service is unavailable (%s), returning empty member list for org head %s".formatted(reason, headUserId));
                return List.of();
            }
        };
    }
}
