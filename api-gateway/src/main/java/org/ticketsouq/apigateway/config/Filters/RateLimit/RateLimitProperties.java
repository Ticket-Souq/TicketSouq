package org.ticketsouq.apigateway.config.Filters.RateLimit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    private List<String> paths = List.of("/**");
    private long capacity = 20;
    private long refill = 20;
    private Duration refillPeriod = Duration.ofMinutes(1);
    private List<String> allowedOrigins = List.of();

}
