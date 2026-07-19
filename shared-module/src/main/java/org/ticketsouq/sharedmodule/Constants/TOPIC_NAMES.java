package org.ticketsouq.sharedmodule.Constants;

public final class TOPIC_NAMES {

    private TOPIC_NAMES() {
    }

    // ─── Auth / API Gateway ───────────────────────────────────
    public static final String USER_EMAIL_VERIFICATION = "user.email-verification";
    public static final String USER_PASSWORD_RESET = "user.password-reset";
    public static final String USER_PASSWORD_CHANGE = "user.password-change";
    public static final String ACCOUNTS_GENERATED = "accounts.generated";

//    public static final String USER_REGISTERED = "user.registered";
//    public static final String USER_LOGGED_IN = "user.logged.in";
//    public static final String USER_LOGGED_OUT = "user.logged.out";
//
    // ─── User Service ─────────────────────────────────────────
//    public static final String USER_EMAIL_UPDATED = "user.email.updated";
//    public static final String USER_PROFILE_UPDATED = "user.profile.updated";
//    public static final String USER_DELETED = "user.deleted";
//
    // ─── Event Service ────────────────────────────────────────
    public static final String EVENT_CREATED = "event.created";
    public static final String EVENT_ACTIVATED = "event.activated";
    public static final String EVENT_COMPLETED = "event.completed";
    public static final String EVENT_PAYOUT_RELEASED = "event.payout-released";
    public static final String EVENT_CANCELLED = "event.cancelled";

    // ─── Venue Service ────────────────────────────────────────
//    public static final String VENUE_CREATED = "venue.created";
//    public static final String VENUE_UPDATED = "venue.updated";
//
//     ─── Ticket Service ───────────────────────────────────────
//    public static final String TICKET_CREATED = "ticket.created";
//    public static final String TICKET_RESERVED = "ticket.reserved";
//    public static final String TICKET_SOLD = "ticket.sold";
//    public static final String TICKET_CANCELLED = "ticket.cancelled";
//    public static final String TICKET_AVAILABILITY_CHANGED = "ticket.availability.changed";
//
    // ─── Reservation Service ──────────────────────────────────
//    public static final String RESERVATION_CREATED = "reservation.created";
//    public static final String RESERVATION_CONFIRMED = "reservation.confirmed";
//    public static final String RESERVATION_EXPIRED = "reservation.expired";
//    public static final String RESERVATION_CANCELLED = "reservation.cancelled";
//
    // ─── Payment Service ──────────────────────────────────────
//    public static final String PAYMENT_INITIATED = "payment.initiated";
    public static final String PAYMENT_SUCCESS = "payment.success";
//    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String PAYMENT_REFUNDED = "payment.refunded";

//
    // ─── Notification Service ─────────────────────────────────
    public static final String NOTIFICATION_SENT = "notification.sent";

    public static final String NOTIFICATION_EMAIL_VERIFICATION = "notification.email-verification";
//    public static final String NOTIFICATION_SENT = "notification.sent";
//    public static final String NOTIFICATION_SENT = "notification.sent";
//    public static final String NOTIFICATION_SENT = "notification.sent";
//    public static final String NOTIFICATION_SENT = "notification.sent";



    // ─── Audit Service ────────────────────────────────────────
    public static final String AUDIT_EVENT = "audit.event";
//    public static final String ACCOUNTS_GENERATED = "accounts.generated";

    // ─── Analytics Service ────────────────────────────────────
//    public static final String ANALYTICS_EVENT = "analytics.event";
//
    // ─── Availability Locking Service ─────────────────────────
//    public static final String SEAT_LOCKED = "seat.locked";
//    public static final String SEAT_UNLOCKED = "seat.unlocked";
//    public static final String SEAT_LOCK_EXPIRED = "seat.lock.expired";
}
