package org.ticketsouq.apigateway.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class GatewayMetrics {

    private final MeterRegistry registry;
    private final Timer requestTimer;

    public GatewayMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.requestTimer = Timer.builder("gateway.request.duration")
            .description("API Gateway request duration")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(registry);
    }

    public void recordRequest(String method, String route, int statusCode) {
        String outcome = statusCode < 400 ? "SUCCESS" : statusCode < 500 ? "CLIENT_ERROR" : "SERVER_ERROR";
        Counter.builder("gateway.requests")
            .description("Total gateway requests")
            .tag("method", method)
            .tag("route", normalizeRoute(route))
            .tag("status", String.valueOf(statusCode))
            .tag("outcome", outcome)
            .register(registry)
            .increment();
    }

    public void recordRequestDuration(String method, String route, long durationMillis) {
        Timer.builder("gateway.request.duration")
            .description("API Gateway request duration")
            .tag("method", method)
            .tag("route", normalizeRoute(route))
            .register(registry)
            .record(durationMillis, TimeUnit.MILLISECONDS);
    }

    public void recordAuthFailure(String reason) {
        Counter.builder("gateway.auth.failures")
            .description("Authentication failures")
            .tag("reason", reason)
            .register(registry)
            .increment();
    }

    public void recordRateLimitExceeded() {
        Counter.builder("gateway.rate_limit.exceeded")
            .description("Rate limit rejections")
            .register(registry)
            .increment();
    }

    private String normalizeRoute(String route) {
        if (route == null) return "unknown";
        if (route.startsWith("/api/")) {
            String[] parts = route.split("/");
            if (parts.length >= 4) {
                return "/" + parts[1] + "/" + parts[2] + "/" + parts[3];
            }
        }
        return route;
    }
}
