package org.ticketsouq.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
            new SecurityRule(List.of("/api/v1/private/**"), SecurityRule.Access.DENY_ALL, null),
            new SecurityRule(List.of("/api/v1/user/org/generate-accounts"), SecurityRule.Access.HAS_ROLE, List.of("ORG_HEAD"))
        );
    }
}
