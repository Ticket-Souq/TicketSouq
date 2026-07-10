package org.ticketsouq.apigateway.config.Filters.RateLimit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    // Ant-style path patterns to rate-limit (e.g. /api/v1/auth/**)
    private List<String> paths = List.of("/api/v1/auth/**");

    // Max tokens the bucket can hold — controls burst size
    private long capacity = 20;

    // Tokens added at the start of each refill period
    private long refill = 20;

    // How often tokens are refilled (e.g. 1m = 20 req/min steady state)
    private Duration refillPeriod = Duration.ofMinutes(1);

}
