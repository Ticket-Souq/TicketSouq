-- V1: initial schema for ticket-service (ticket_db)

CREATE TABLE tickets (
    id                 uuid NOT NULL,
    ticket_type        varchar(31) NOT NULL,
    reservation_id     uuid,
    event_id           uuid NOT NULL,
    user_id            uuid NOT NULL,
    consumed           boolean NOT NULL,
    price              numeric(38, 2),
    reservation_status varchar(255),
    holder_name        varchar(255),
    created_at         timestamp,
    updated_at         timestamp,
    section_id         uuid,
    category           varchar(255),
    seat_id            uuid,
    template_seat_id   uuid,
    seat_row           varchar(255),
    seat_number        integer,
    CONSTRAINT pk_tickets PRIMARY KEY (id)
);

CREATE TABLE event_snapshots (
    event_id          uuid NOT NULL,
    title             varchar(255),
    description       varchar(255),
    venue_template_id uuid,
    organization      varchar(255),
    status            varchar(255),
    category_name     varchar(255),
    poster_url        varchar(255),
    start_date        timestamp with time zone,
    finish_date       timestamp with time zone,
    CONSTRAINT pk_event_snapshots PRIMARY KEY (event_id)
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
