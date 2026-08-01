-- V1: initial schema for api-gateway (auth_db)

CREATE TABLE auth_credential (
    user_id         uuid                        NOT NULL,
    email           varchar(255)                NOT NULL,
    password_hash   varchar(255)                NOT NULL,
    role            varchar(255)                NOT NULL,
    is_active       boolean,
    is_verified     boolean,
    last_login      timestamp with time zone,
    failed_attempts integer,
    is_locked       boolean,
    locked_until    timestamp with time zone,
    CONSTRAINT pk_auth_credential PRIMARY KEY (user_id),
    CONSTRAINT uq_auth_credential_email UNIQUE (email)
);

CREATE TABLE refresh_token (
    id          uuid                        NOT NULL,
    user_id     uuid                        NOT NULL,
    session_id  uuid                        NOT NULL,
    revoked     boolean,
    expiry_date timestamp with time zone    NOT NULL,
    CONSTRAINT pk_refresh_token PRIMARY KEY (id),
    CONSTRAINT uq_refresh_token_session_id UNIQUE (session_id)
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
