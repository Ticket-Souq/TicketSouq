package org.ticketsouq.notificationservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class NotificationMetrics {

    private final MeterRegistry registry;
    private final Timer emailDeliveryTimer;

    public NotificationMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.emailDeliveryTimer = Timer.builder("notifications.email.delivery.time")
            .description("Time taken to deliver an email")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(registry);
    }

    public void recordEmailSent(String template) {
        Counter.builder("notifications.email.sent")
            .description("Total emails sent")
            .tag("template", template)
            .tag("status", "SENT")
            .register(registry)
            .increment();
    }

    public void recordEmailFailed(String template, String reason) {
        Counter.builder("notifications.email.sent")
            .description("Total emails sent")
            .tag("template", template)
            .tag("status", "FAILED")
            .register(registry)
            .increment();
    }

    public void recordEmailDeliveryTime(long durationMillis) {
        emailDeliveryTimer.record(durationMillis, TimeUnit.MILLISECONDS);
    }

    public void recordInAppNotification(String type) {
        Counter.builder("notifications.inapp.created")
            .description("Total in-app notifications created")
            .tag("type", type)
            .register(registry)
            .increment();
    }
}
