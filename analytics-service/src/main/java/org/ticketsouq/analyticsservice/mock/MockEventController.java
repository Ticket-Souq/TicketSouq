package org.ticketsouq.analyticsservice.mock;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * MOCK CONTROLLER — delete the entire {@code mock/} package when real producer services are integrated.
 *
 * <p>REST endpoints to manually trigger Kafka events for testing the Analytics Service.
 * Every endpoint publishes an event to the <b>same Kafka topic</b> that the production
 * services use, with the <b>same serialization format</b> ({@code __TypeId__} headers
 * enabled, key = entity UUID as String).
 *
 * <h3>Quick start</h3>
 * <pre>{@code
 * # 1) Publish the full mock scenario (3 events + 40 payments + refund + failure)
 * curl -X POST http://localhost:4234/api/mock/publish-all
 *
 * # 2) Check the analytics KPIs
 * curl http://localhost:4234/api/analytics/overview/kpis
 *
 * # 3) Check event summary
 * curl http://localhost:4234/api/analytics/events/00000000-0000-0000-0000-000000000001/summary
 *
 * # 4) Sales timeline
 * curl "http://localhost:4234/api/analytics/events/00000000-0000-0000-0000-000000000001/sales-timeline?granularity=day"
 * }</pre>
 *
 * <h3>Sample JSON payloads (for reference — these match the Java records sent to Kafka)</h3>
 * <pre>{@code
 * EventCreatedEvent:
 * {
 *   "eventId": "00000000-0000-0000-0000-000000000001",
 *   "title": "Summer Music Festival",
 *   "organization": "LiveNation",
 *   "createdby": "00000000-0000-0000-0000-000000000010",
 *   "bookingModel": "GENERAL",
 *   "startDateTime": "2026-08-15T16:00:00Z",
 *   "endDateTime": "2026-08-16T23:00:00Z"
 * }
 *
 * EventActivatedEvent:
 * { "eventId": "00000000-0000-0000-0000-000000000001", "activatedAt": "2026-03-02T10:00:00Z" }
 *
 * EventCompletedEvent:
 * { "eventId": "00000000-0000-0000-0000-000000000001", "completedAt": "2026-08-17T02:00:00Z" }
 *
 * EventCancelledEvent:
 * {
 *   "messageId": "some-uuid",
 *   "eventId": "00000000-0000-0000-0000-000000000003",
 *   "organizationId": "00000000-0000-0000-0000-000000000030",
 *   "cancelledAt": "2026-07-15T14:00:00Z"
 * }
 *
 * PaymentSuccessEvent:
 * {
 *   "messageId": "some-uuid",
 *   "userId": "00000000-0000-0000-0000-000000000100",
 *   "eventId": "00000000-0000-0000-0000-000000000001",
 *   "amount": 150.00
 * }
 *
 * PaymentFailedEvent:
 * {
 *   "messageId": "some-uuid",
 *   "userId": "00000000-0000-0000-0000-000000000101",
 *   "eventId": "00000000-0000-0000-0000-000000000002",
 *   "amount": 250.00
 * }
 *
 * RefundCompletedEvent:
 * {
 *   "messageId": "some-uuid",
 *   "userId": "00000000-0000-0000-0000-000000000102",
 *   "eventId": "00000000-0000-0000-0000-000000000001",
 *   "amount": 150.00
 * }
 * }</pre>
 */
@RestController
@RequestMapping("/api/mock")
@RequiredArgsConstructor
@Tag(name = "🔧 Mock Producer", description = "Temporary — publishes synthetic Kafka events for Analytics Service testing. DELETE this package when real producers exist.")
public class MockEventController {

    private final MockEventPublisher publisher;

    // ─── Full scenario ───────────────────────────────────────

    @PostMapping("/publish-all")
    @Operation(summary = "Publish the complete mock scenario (3 events + payments + refund + failure)")
    public ResponseEntity<Map<String, String>> publishAll() {
        publisher.publishAll();
        return ResponseEntity.ok(Map.of(
            "status", "ok",
            "message", "Full mock dataset published to Kafka"
        ));
    }

    // ─── Event lifecycle ─────────────────────────────────────

    @PostMapping("/events/{eventNum}/created")
    @Operation(summary = "Publish EventCreatedEvent for mock event 1, 2, or 3")
    public ResponseEntity<Map<String, String>> publishEventCreated(@PathVariable int eventNum) {
        publisher.publishEventCreated(eventNum);
        return ok("EventCreatedEvent", eventNum);
    }

    @PostMapping("/events/{eventNum}/activated")
    @Operation(summary = "Publish EventActivatedEvent (available for events 1, 3)")
    public ResponseEntity<Map<String, String>> publishEventActivated(@PathVariable int eventNum) {
        publisher.publishEventActivated(eventNum);
        return ok("EventActivatedEvent", eventNum);
    }

    @PostMapping("/events/{eventNum}/completed")
    @Operation(summary = "Publish EventCompletedEvent (available for event 1)")
    public ResponseEntity<Map<String, String>> publishEventCompleted(@PathVariable int eventNum) {
        publisher.publishEventCompleted(eventNum);
        return ok("EventCompletedEvent", eventNum);
    }

    @PostMapping("/events/{eventNum}/cancelled")
    @Operation(summary = "Publish EventCancelledEvent (available for event 3)")
    public ResponseEntity<Map<String, String>> publishEventCancelled(@PathVariable int eventNum) {
        publisher.publishEventCancelled(eventNum);
        return ok("EventCancelledEvent", eventNum);
    }

    // ─── Payments ────────────────────────────────────────────

    @PostMapping("/payments/success")
    @Operation(summary = "Publish ~20 PaymentSuccessEvent for Event 1")
    public ResponseEntity<Map<String, String>> publishPaymentSuccess() {
        publisher.publishPaymentSuccess();
        return ResponseEntity.ok(Map.of(
            "status", "ok",
            "message", "PaymentSuccessEvent batch published"
        ));
    }

    @PostMapping("/payments/failed")
    @Operation(summary = "Publish a single PaymentFailedEvent for Event 2")
    public ResponseEntity<Map<String, String>> publishPaymentFailed() {
        publisher.publishPaymentFailed();
        return ResponseEntity.ok(Map.of(
            "status", "ok",
            "message", "PaymentFailedEvent published"
        ));
    }

    @PostMapping("/payments/refund")
    @Operation(summary = "Publish a single RefundCompletedEvent for Event 1")
    public ResponseEntity<Map<String, String>> publishRefund() {
        publisher.publishRefund();
        return ResponseEntity.ok(Map.of(
            "status", "ok",
            "message", "RefundCompletedEvent published"
        ));
    }

    // ─── Info ────────────────────────────────────────────────

    @GetMapping("/events")
    @Operation(summary = "List mock event identifiers for reference")
    public ResponseEntity<Map<String, String>> listMockEvents() {
        return ResponseEntity.ok(Map.of(
            "Event 1 (Summer Music Festival)", MockDataFactory.EVENT_1_ID.toString(),
            "Event 2 (Tech Conference 2026)", MockDataFactory.EVENT_2_ID.toString(),
            "Event 3 (Food & Wine Expo)", MockDataFactory.EVENT_3_ID.toString()
        ));
    }

    // ─── Helpers ─────────────────────────────────────────────

    private static ResponseEntity<Map<String, String>> ok(String eventType, int eventNum) {
        return ResponseEntity.ok(Map.of(
            "status", "ok",
            "message", eventType + " for event " + eventNum + " published"
        ));
    }
}
