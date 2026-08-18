-- V1: initial schema for event-service (event_db)

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE event_categories (
    id   uuid NOT NULL,
    name varchar(255) NOT NULL,
    CONSTRAINT pk_event_categories PRIMARY KEY (id),
    CONSTRAINT uq_event_categories_name UNIQUE (name)
);

CREATE TABLE events (
    id                uuid                        NOT NULL,
    title             varchar(255)                NOT NULL,
    description       text,
    location          varchar(255)                NOT NULL,
    venue_template_id uuid,
    event_category_id uuid,
    organization      varchar(255),
    created_by_id     uuid,
    poster_url        text                        NOT NULL,
    banner_url        text,
    status            varchar(255)                NOT NULL,
    booking_model     varchar(255)                NOT NULL,
    start_date_time   timestamp with time zone    NOT NULL,
    end_date_time     timestamp with time zone    NOT NULL,
    created_at        timestamp,
    CONSTRAINT pk_events PRIMARY KEY (id),
    CONSTRAINT fk_events_category FOREIGN KEY (event_category_id) REFERENCES event_categories (id)
);

CREATE TABLE sections (
    id                  uuid NOT NULL,
    template_section_id uuid,
    event_id            uuid NOT NULL,
    name                varchar(255) NOT NULL,
    capacity            integer,
    remaining_capacity  integer,
    color               varchar(255),
    price               numeric(38, 2),
    updated_at          timestamp,
    CONSTRAINT pk_sections PRIMARY KEY (id),
    CONSTRAINT uq_sections_event_name UNIQUE (event_id, name),
    CONSTRAINT fk_sections_event FOREIGN KEY (event_id) REFERENCES events (id)
);

CREATE TABLE seats (
    id               uuid NOT NULL,
    template_seat_id uuid,
    section_id       uuid NOT NULL,
    lable            varchar(255) NOT NULL,
    status           varchar(255) NOT NULL,
    updated_at       timestamp,
    CONSTRAINT pk_seats PRIMARY KEY (id),
    CONSTRAINT fk_seats_section FOREIGN KEY (section_id) REFERENCES sections (id)
);

CREATE TABLE seat_locks (
    id             uuid                        NOT NULL,
    seat_id        uuid                        NOT NULL,
    reservation_id varchar(255)                NOT NULL,
    expires_at     timestamp                   NOT NULL,
    created_at     timestamp,
    CONSTRAINT pk_seat_locks PRIMARY KEY (id),
    CONSTRAINT uq_seat_locks_seat_id UNIQUE (seat_id)
);
CREATE INDEX idx_seat_lock_expires ON seat_locks (expires_at);
CREATE INDEX idx_seat_lock_reservation ON seat_locks (reservation_id);

CREATE TABLE zone_locks (
    id             uuid                        NOT NULL,
    zone_id        uuid                        NOT NULL,
    reservation_id varchar(255)                NOT NULL,
    quantity       integer                     NOT NULL,
    expires_at     timestamp                   NOT NULL,
    created_at     timestamp,
    CONSTRAINT pk_zone_locks PRIMARY KEY (id)
);
CREATE INDEX idx_zone_lock_zone ON zone_locks (zone_id);
CREATE INDEX idx_zone_lock_expires ON zone_locks (expires_at);
CREATE INDEX idx_zone_lock_reservation ON zone_locks (reservation_id);

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
