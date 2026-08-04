package org.ticketsouq.reservationservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class SagaMetrics {

    private final MeterRegistry registry;
    private final AtomicInteger activeSagas = new AtomicInteger(0);
    private final Counter sagaCompletedCounter;
    private final Counter sagaFailedCounter;
    private final Counter sagaCompensationCounter;
    private final Counter sagaTimeoutCounter;
    private final Timer sagaDurationTimer;

    public SagaMetrics(MeterRegistry registry) {
        this.registry = registry;

        Gauge.builder("saga.active", activeSagas, AtomicInteger::doubleValue)
            .description("Number of currently active sagas")
            .register(registry);

        this.sagaCompletedCounter = Counter.builder("saga.completed")
            .description("Total completed sagas")
            .register(registry);

        this.sagaFailedCounter = Counter.builder("saga.failed")
            .description("Total failed sagas")
            .register(registry);

        this.sagaCompensationCounter = Counter.builder("saga.compensation.triggered")
            .description("Total saga compensations triggered")
            .register(registry);

        this.sagaTimeoutCounter = Counter.builder("saga.timeout.recovered")
            .description("Total sagas recovered due to timeout")
            .register(registry);

        this.sagaDurationTimer = Timer.builder("saga.duration")
            .description("Time from saga start to completion or failure")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(registry);
    }

    public void sagaStarted() {
        activeSagas.incrementAndGet();
    }

    public void sagaCompleted(long durationMillis) {
        activeSagas.decrementAndGet();
        sagaCompletedCounter.increment();
        sagaDurationTimer.record(durationMillis, TimeUnit.MILLISECONDS);
    }

    public void sagaFailed(String failedStep, long durationMillis) {
        activeSagas.decrementAndGet();
        Counter.builder("saga.failed")
            .description("Total failed sagas")
            .tag("failed_step", failedStep)
            .register(registry)
            .increment();
        sagaDurationTimer.record(durationMillis, TimeUnit.MILLISECONDS);
    }

    public void compensationTriggered() {
        sagaCompensationCounter.increment();
    }

    public void timeoutRecovered() {
        sagaTimeoutCounter.increment();
    }
}
