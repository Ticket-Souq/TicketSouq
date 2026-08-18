-- V1: initial schema for reservation-service (reservation_db)

CREATE TABLE reservations (
    id           uuid                        NOT NULL,
    user_id      uuid                        NOT NULL,
    event_id     uuid                        NOT NULL,
    status       varchar(255)                NOT NULL,
    created_at   timestamp with time zone,
    completed_at timestamp with time zone,
    CONSTRAINT pk_reservations PRIMARY KEY (id)
);

CREATE TABLE saga_instances (
    id                    uuid                        NOT NULL,
    reservation_id        uuid                        NOT NULL,
    user_id               uuid                        NOT NULL,
    event_id              uuid                        NOT NULL,
    saga_status           varchar(255)                NOT NULL,
    current_step          varchar(255)                NOT NULL,
    payment_id            uuid,
    total_amount          numeric(19, 2),
    ticket_details        jsonb,
    fail_reason           text,
    version               integer,
    created_at            timestamp with time zone,
    updated_at            timestamp with time zone,
    completed_at          timestamp with time zone,
    last_step_completed_at timestamp with time zone,
    CONSTRAINT pk_saga_instances PRIMARY KEY (id),
    CONSTRAINT uq_saga_instances_reservation UNIQUE (reservation_id)
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
