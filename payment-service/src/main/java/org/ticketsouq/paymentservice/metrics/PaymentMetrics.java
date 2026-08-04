package org.ticketsouq.paymentservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class PaymentMetrics {

    private final MeterRegistry registry;
    private final Timer paymentProcessingTimer;
    private final Counter paymentSuccessCounter;
    private final Counter paymentFailedCounter;
    private final Counter paymentRefundCounter;
    private final DistributionSummary paymentAmountSummary;

    public PaymentMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.paymentProcessingTimer = Timer.builder("payments.processing.time")
            .description("Time taken to process a payment")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(registry);
        this.paymentSuccessCounter = Counter.builder("payments.total")
            .description("Total payment attempts")
            .tag("status", "SUCCESS")
            .register(registry);
        this.paymentFailedCounter = Counter.builder("payments.total")
            .description("Total payment attempts")
            .tag("status", "FAILED")
            .register(registry);
        this.paymentRefundCounter = Counter.builder("payments.total")
            .description("Total payment attempts")
            .tag("status", "REFUNDED")
            .register(registry);
        this.paymentAmountSummary = DistributionSummary.builder("payments.amount")
            .description("Payment amount distribution")
            .publishPercentiles(0.5, 0.95, 0.99)
            .baseUnit("currency")
            .register(registry);
    }

    public void recordPaymentProcessing(long durationMillis) {
        paymentProcessingTimer.record(durationMillis, TimeUnit.MILLISECONDS);
    }

    public void recordPaymentSuccess(double amount) {
        paymentSuccessCounter.increment();
        paymentAmountSummary.record(amount);
    }

    public void recordPaymentFailed(double amount) {
        paymentFailedCounter.increment();
    }

    public void recordPaymentRefund(double amount) {
        paymentRefundCounter.increment();
        Counter.builder("payments.refunds.total")
            .description("Total refunds")
            .register(registry)
            .increment();
    }
}
