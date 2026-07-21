package org.ticketsouq.analyticsservice.mock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.ticketsouq.sharedmodule.PaymentService.events.PaymentSuccessEvent;
import org.ticketsouq.sharedmodule.utils.LogUtils;

import java.util.List;

import static org.ticketsouq.sharedmodule.Constants.SERVICE_NAMES.ANALYTICS_SERVICE;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.*;

/**
 * MOCK CLASS — delete the entire {@code mock/} package when real producer services are integrated.
 *
 * Publishes synthetic Kafka events that match the schemas expected by the Analytics Service
 * consumer ({@link org.ticketsouq.analyticsservice.consumer.AnalyticsEventConsumer}).
 *
 * <p>Uses the <b>same</b> {@link KafkaTemplate} auto-configured for the DLT error handler,
 * which has {@code spring.json.add.type.headers=true} so every outgoing message carries a
 * {@code __TypeId__} header matching the production producer format.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    // ─────────────────────────────────────────────────────────
    //  Startup publish (opt-in via app.mock.publish-on-startup)
    // ─────────────────────────────────────────────────────────

    @EventListener(ApplicationReadyEvent.class)
    public void publishOnStartup() {
        String flag = System.getProperty("app.mock.publish-on-startup", "false");
        if ("true".equalsIgnoreCase(flag) || "yes".equalsIgnoreCase(flag)) {
            log.info("⚡ app.mock.publish-on-startup = true — publishing full mock dataset");
            publishAll();
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Full scenario
    // ─────────────────────────────────────────────────────────

    /** Publish the complete mock scenario: 3 events + payments + refund + failure. */
    public void publishAll() {
        // --- Event lifecycle ---
        publishEventCreated(1);
        publishEventActivated(1);

        // Event 3 created + activated (will be cancelled later)
        publishEventCreated(3);
        publishEventActivated(3);

        // Event 2 created (stays in CREATED)
        publishEventCreated(2);

        // --- Payments ---
        publishAll(MockDataFactory.event1Payments());
        publishAll(MockDataFactory.event2Payments());
        publishAll(MockDataFactory.event3Payments());

        // --- Refund ---
        kafkaTemplate.send(PAYMENT_REFUNDED,
            MockDataFactory.event1Refund().userId().toString(),
            MockDataFactory.event1Refund());

        // --- Failed payment ---
        kafkaTemplate.send(PAYMENT_FAILED,
            MockDataFactory.event2FailedPayment().userId().toString(),
            MockDataFactory.event2FailedPayment());

        // --- Complete Event 1, cancel Event 3 ---
        publishEventCompleted(1);
        publishEventCancelled(3);

        log.info("Mock dataset published — 3 events, ~40 payments, 1 refund, 1 failure.");
    }

    // ─────────────────────────────────────────────────────────
    //  Individual event publishers
    // ─────────────────────────────────────────────────────────

    public void publishEventCreated(int eventNum) {
        var event = switch (eventNum) {
            case 1 -> MockDataFactory.event1Created();
            case 2 -> MockDataFactory.event2Created();
            case 3 -> MockDataFactory.event3Created();
            default -> throw new IllegalArgumentException("Unknown event number: " + eventNum);
        };
        kafkaTemplate.send(EVENT_CREATED, event.eventId().toString(), event);
        LogUtils.logEventPublished(ANALYTICS_SERVICE, EVENT_CREATED);
    }

    public void publishEventActivated(int eventNum) {
        var event = switch (eventNum) {
            case 1 -> MockDataFactory.event1Activated();
            case 3 -> MockDataFactory.event3Activated();
            default -> throw new IllegalArgumentException(
                "Event " + eventNum + " has no activation data");
        };
        kafkaTemplate.send(EVENT_ACTIVATED, event.eventId().toString(), event);
        LogUtils.logEventPublished(ANALYTICS_SERVICE, EVENT_ACTIVATED);
    }

    public void publishEventCompleted(int eventNum) {
        if (eventNum != 1)
            throw new IllegalArgumentException("Only Event 1 has completion data");
        var event = MockDataFactory.event1Completed();
        kafkaTemplate.send(EVENT_COMPLETED, event.eventId().toString(), event);
        LogUtils.logEventPublished(ANALYTICS_SERVICE, EVENT_COMPLETED);
    }

    public void publishEventCancelled(int eventNum) {
        if (eventNum != 3)
            throw new IllegalArgumentException("Only Event 3 has cancellation data");
        var event = MockDataFactory.event3Cancelled();
        kafkaTemplate.send(EVENT_CANCELLED, event.eventId().toString(), event);
        LogUtils.logEventPublished(ANALYTICS_SERVICE, EVENT_CANCELLED);
    }

    public void publishPaymentSuccess() {
        var events = MockDataFactory.event1Payments();
        publishAll(events);
    }

    public void publishPaymentFailed() {
        var event = MockDataFactory.event2FailedPayment();
        kafkaTemplate.send(PAYMENT_FAILED, event.userId().toString(), event);
        LogUtils.logEventPublished(ANALYTICS_SERVICE, PAYMENT_FAILED);
    }

    public void publishRefund() {
        var event = MockDataFactory.event1Refund();
        kafkaTemplate.send(PAYMENT_REFUNDED, event.userId().toString(), event);
        LogUtils.logEventPublished(ANALYTICS_SERVICE, PAYMENT_REFUNDED);
    }

    // ─────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────

    private void publishAll(List<PaymentSuccessEvent> events) {
        events.forEach(e ->
            kafkaTemplate.send(PAYMENT_SUCCESS, e.userId().toString(), e)
        );
    }
}
