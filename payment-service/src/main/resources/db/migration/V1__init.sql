-- V1: initial schema for payment-service (payment_db)

CREATE TABLE payment_model (
    id                      uuid NOT NULL,
    reservationid           uuid,
    customerid              uuid,
    amount                  numeric(38, 2),
    payment_status          varchar(255),
    transaction_ref         varchar(255),
    stripe_payment_intent_id varchar(255),
    created_at              timestamp with time zone,
    updated_at              timestamp with time zone,
    CONSTRAINT pk_payment_model PRIMARY KEY (id)
);

CREATE TABLE payout (
    id                 uuid NOT NULL,
    organizer_id       uuid,
    amount             numeric(38, 2),
    currency           varchar(255),
    status             varchar(255),
    stripe_transfer_id varchar(255),
    created_at         timestamp with time zone,
    updated_at         timestamp with time zone,
    CONSTRAINT pk_payout PRIMARY KEY (id)
);

CREATE TABLE ticket_souq_outbox (
    id           uuid                        NOT NULL,
    aggregate_id varchar(255)                NOT NULL,
    event_type   varchar(255)                NOT NULL,
    topic        varchar(255)                NOT NULL,
    payload      text                        NOT NULL,
    status       varchar(20)                 NOT NULL,
    retry_count  integer                     NOT NULL,
    created_at   timestamp with time zone    NOT NULL,
    claimed_at   timestamp with time zone,
    published_at timestamp with time zone,
    CONSTRAINT pk_ticket_souq_outbox PRIMARY KEY (id)
);
