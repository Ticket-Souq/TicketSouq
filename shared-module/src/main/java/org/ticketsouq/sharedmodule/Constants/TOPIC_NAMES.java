package org.ticketsouq.sharedmodule.Constants;

public final class TOPIC_NAMES {

    private TOPIC_NAMES() {
    }

    // ─── Auth / API Gateway ───────────────────────────────────
    public static final String USER_EMAIL_VERIFICATION = "user.email-verification";
    public static final String USER_PASSWORD_RESET = "user.password-reset";
    public static final String USER_PASSWORD_CHANGE = "user.password-change";
    public static final String ACCOUNTS_GENERATED = "accounts.generated";

    // ─── Event Service ────────────────────────────────────────
    public static final String EVENT_CREATED = "event.created";
    public static final String EVENT_ACTIVATED = "event.activated";
    public static final String EVENT_COMPLETED = "event.completed";
    public static final String EVENT_PAYOUT_RELEASED = "event.payout-released";
    public static final String EVENT_CANCELLED = "event.cancelled";

    // ─── Payment Service ──────────────────────────────────────
    public static final String PAYMENT_SUCCESS = "payment.success";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String PAYMENT_REFUNDED = "payment.refunded";
    public static final String PAYMENT_REFUND_REQUEST = "payment.refund.request";

    // ─── Saga (Reservation Service) ───────────────────────────
    public static final String RESERVATION_BEGIN = "reservation.begin";
    public static final String SAGA_PAYMENT_COMMAND = "saga.payment.command";
    public static final String SAGA_PAYMENT_REPLY = "saga.payment.reply";
    public static final String SAGA_PAYMENT_COMPENSATE = "saga.payment.compensate";

    public static final String SAGA_TICKET_COMMAND = "saga.ticket.command";
    public static final String SAGA_TICKET_REPLY = "saga.ticket.reply";
    public static final String SAGA_TICKET_COMPENSATE = "saga.ticket.compensate";

    public static final String SAGA_LOCK_CONFIRM_COMMAND = "saga.lock.confirm.command";
    public static final String SAGA_LOCK_CONFIRM_COMPENSATE = "saga.lock.confirm.compensate";
    public static final String SAGA_LOCK_CONFIRM_REPLY = "saga.lock.confirm.reply";

    // ─── Audit Service ────────────────────────────────────────
    public static final String AUDIT_EVENT = "audit.event";

}
