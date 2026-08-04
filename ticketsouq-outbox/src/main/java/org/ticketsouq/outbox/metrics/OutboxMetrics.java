package org.ticketsouq.outbox.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class OutboxMetrics {

    private final MeterRegistry registry;
    private final AtomicInteger pendingEvents = new AtomicInteger(0);
    private final Counter publishedCounter;
    private final Counter failedCounter;
    private final Counter retryCounter;
    private final Timer processingTimer;

    public OutboxMetrics(MeterRegistry registry) {
        this.registry = registry;

        Gauge.builder("outbox.events.pending", pendingEvents, AtomicInteger::doubleValue)
            .description("Number of pending outbox events")
            .register(registry);

        this.publishedCounter = Counter.builder("outbox.events.processed")
            .description("Total outbox events processed")
            .tag("status", "PUBLISHED")
            .register(registry);

        this.failedCounter = Counter.builder("outbox.events.processed")
            .description("Total outbox events processed")
            .tag("status", "FAILED")
            .register(registry);

        this.retryCounter = Counter.builder("outbox.events.retry")
            .description("Total outbox event retries")
            .register(registry);

        this.processingTimer = Timer.builder("outbox.processing.time")
            .description("Time to process a batch of outbox events")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(registry);
    }

    public void setPendingCount(int count) {
        pendingEvents.set(count);
    }

    public void recordPublished() {
        publishedCounter.increment();
    }

    public void recordFailed() {
        failedCounter.increment();
    }

    public void recordRetry() {
        retryCounter.increment();
    }

    public void recordProcessingTime(long durationMillis) {
        processingTimer.record(durationMillis, TimeUnit.MILLISECONDS);
    }
}
