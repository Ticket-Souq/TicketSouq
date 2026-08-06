package org.ticketsouq.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import java.util.List;

@Configuration
public class SecurityRulesConfig {

    @Bean
    public List<SecurityRule> securityRules() {
        return List.of(
            new SecurityRule(
                List.of("/eureka/**", "/swagger-ui.html", "/swagger-ui/**",
                        "/v3/api-docs/**", "/actuator/**", "/aggregate/*/v3/api-docs"),
                SecurityRule.Access.PERMIT_ALL, null
            ),
            new SecurityRule(List.of("/api/v1/auth/**"), SecurityRule.Access.PERMIT_ALL, null),
//            new SecurityRule(List.of("/api/v1/private/**"), SecurityRule.Access.DENY_ALL, null),
            new SecurityRule(List.of("/api/v1/auth/org/**"), SecurityRule.Access.HAS_ROLE, List.of("ORG_HEAD")),
            new SecurityRule(List.of("/api/v1/event/*"), HttpMethod.DELETE, SecurityRule.Access.HAS_ROLE, List.of("ORG_HEAD", "ADMIN")),
            new SecurityRule(List.of("/api/v1/event/management"), SecurityRule.Access.HAS_ANY_ROLE, List.of("ORG_HEAD", "ORG_Agent")),
            new SecurityRule(List.of("/api/v1/user/org/**"), SecurityRule.Access.HAS_ROLE, List.of("ADMIN")),
            new SecurityRule(List.of("/api/v1/user/organizations"), SecurityRule.Access.HAS_ROLE, List.of("ADMIN")),
            new SecurityRule(List.of("/api/v1/audit"), SecurityRule.Access.HAS_ROLE, List.of("ADMIN")),
            new SecurityRule(List.of("/api/v1/event/locks/**"), SecurityRule.Access.HAS_ROLE, List.of("CUSTOMER"))
//        new SecurityRule(List.of("/api/v1/event/locks/**"), SecurityRule.Access.PERMIT_ALL, null)
        );
    }
}
