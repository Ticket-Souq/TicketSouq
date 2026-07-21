package org.ticketsouq.analyticsservice.mock;

import org.ticketsouq.sharedmodule.EventService.events.*;
import org.ticketsouq.sharedmodule.PaymentService.events.PaymentFailedEvent;
import org.ticketsouq.sharedmodule.PaymentService.events.PaymentSuccessEvent;
import org.ticketsouq.sharedmodule.PaymentService.events.RefundCompletedEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * MOCK CLASS — delete the entire {@code mock/} package when real producer services are integrated.
 *
 * Generates realistic sample data for testing the Analytics Service end-to-end.
 */
public final class MockDataFactory {

    // ─────────────────────────────────────────────────────────
    //  Fixed identifiers (easy to recognise in DB / logs)
    // ─────────────────────────────────────────────────────────

    public static final UUID EVENT_1_ID  = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID EVENT_2_ID  = UUID.fromString("00000000-0000-0000-0000-000000000002");
    public static final UUID EVENT_3_ID  = UUID.fromString("00000000-0000-0000-0000-000000000003");

    public static final UUID ORGANIZER_1 = UUID.fromString("00000000-0000-0000-0000-000000000010");
    public static final UUID ORGANIZER_2 = UUID.fromString("00000000-0000-0000-0000-000000000020");
    public static final UUID ORGANIZER_3 = UUID.fromString("00000000-0000-0000-0000-000000000030");

    // User "pool" – each payment picks a random-ish dedicated user so the key stays unique
    private static final UUID[] USERS = {
        UUID.fromString("00000000-0000-0000-0000-000000000100"),
        UUID.fromString("00000000-0000-0000-0000-000000000101"),
        UUID.fromString("00000000-0000-0000-0000-000000000102"),
        UUID.fromString("00000000-0000-0000-0000-000000000103"),
        UUID.fromString("00000000-0000-0000-0000-000000000104"),
        UUID.fromString("00000000-0000-0000-0000-000000000105"),
        UUID.fromString("00000000-0000-0000-0000-000000000106"),
        UUID.fromString("00000000-0000-0000-0000-000000000107"),
        UUID.fromString("00000000-0000-0000-0000-000000000108"),
        UUID.fromString("00000000-0000-0000-0000-000000000109"),
    };

    private static final String ORG_NAME_1 = "LiveNation";
    private static final String ORG_NAME_2 = "TechCorp";
    private static final String ORG_NAME_3 = "GourmetEvents";

    private static int userIndex = 0;

    private MockDataFactory() {}

    // ─────────────────────────────────────────────────────────
    //  Event 1 — Summer Music Festival (Full lifecycle)
    // ─────────────────────────────────────────────────────────

    public static EventCreatedEvent event1Created() {
        return new EventCreatedEvent(
            EVENT_1_ID, "Summer Music Festival", ORG_NAME_1, ORGANIZER_1,
            "GENERAL",
            Instant.parse("2026-08-15T16:00:00Z"),
            Instant.parse("2026-08-16T23:00:00Z")
        );
    }

    public static EventActivatedEvent event1Activated() {
        return new EventActivatedEvent(EVENT_1_ID, Instant.parse("2026-03-02T10:00:00Z"));
    }

    public static EventCompletedEvent event1Completed() {
        return new EventCompletedEvent(EVENT_1_ID, Instant.parse("2026-08-17T02:00:00Z"));
    }

    // ─────────────────────────────────────────────────────────
    //  Event 2 — Tech Conference (Created only)
    // ─────────────────────────────────────────────────────────

    public static EventCreatedEvent event2Created() {
        return new EventCreatedEvent(
            EVENT_2_ID, "Tech Conference 2026", ORG_NAME_2, ORGANIZER_2,
            "SEAT",
            Instant.parse("2026-09-10T08:00:00Z"),
            Instant.parse("2026-09-12T18:00:00Z")
        );
    }

    // ─────────────────────────────────────────────────────────
    //  Event 3 — Food & Wine Expo (Created → Cancelled)
    // ─────────────────────────────────────────────────────────

    public static EventCreatedEvent event3Created() {
        return new EventCreatedEvent(
            EVENT_3_ID, "Food & Wine Expo", ORG_NAME_3, ORGANIZER_3,
            "GENERAL",
            Instant.parse("2026-10-05T10:00:00Z"),
            Instant.parse("2026-10-05T22:00:00Z")
        );
    }

    public static EventActivatedEvent event3Activated() {
        return new EventActivatedEvent(EVENT_3_ID, Instant.parse("2026-06-01T08:00:00Z"));
    }

    public static EventCancelledEvent event3Cancelled() {
        return new EventCancelledEvent(
            UUID.randomUUID(), EVENT_3_ID, ORGANIZER_3,
            Instant.parse("2026-07-15T14:00:00Z")
        );
    }

    // ─────────────────────────────────────────────────────────
    //  Payment generators
    // ─────────────────────────────────────────────────────────

    /** 20 payments for Event 1, spread across the last 30 days. */
    public static List<PaymentSuccessEvent> event1Payments() {
        return generatePayments(EVENT_1_ID, 20, 50, 250);
    }

    /** 12 payments for Event 2. */
    public static List<PaymentSuccessEvent> event2Payments() {
        return generatePayments(EVENT_2_ID, 12, 100, 500);
    }

    /** 8 payments for Event 3 (before it was cancelled). */
    public static List<PaymentSuccessEvent> event3Payments() {
        return generatePayments(EVENT_3_ID, 8, 30, 120);
    }

    /** A single refund on Event 1 (reverses one of the larger payments). */
    public static RefundCompletedEvent event1Refund() {
        return new RefundCompletedEvent(
            UUID.randomUUID(),
            nextUser(),
            EVENT_1_ID,
            new BigDecimal("150.00")
        );
    }

    /** A failed payment attempt on Event 2. */
    public static PaymentFailedEvent event2FailedPayment() {
        return new PaymentFailedEvent(
            UUID.randomUUID(),
            nextUser(),
            EVENT_2_ID,
            new BigDecimal("250.00")
        );
    }

    // ─────────────────────────────────────────────────────────
    //  Internal helpers
    // ─────────────────────────────────────────────────────────

    private static List<PaymentSuccessEvent> generatePayments(
            UUID eventId, int count, int minAmount, int maxAmount) {
        List<PaymentSuccessEvent> events = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int amount = minAmount + (int) (Math.random() * (maxAmount - minAmount));
            // Round to nearest 5 for realism
            amount = (int) (Math.round(amount / 5.0) * 5);
            events.add(new PaymentSuccessEvent(
                UUID.randomUUID(),
                nextUser(),
                eventId,
                BigDecimal.valueOf(amount)
            ));
        }
        return events;
    }

    private static UUID nextUser() {
        UUID user = USERS[userIndex % USERS.length];
        userIndex++;
        return user;
    }
}
