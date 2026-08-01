-- V1: initial schema for user-service (user_db)

CREATE TABLE users (
    id         uuid                        NOT NULL,
    name       varchar(100)                NOT NULL,
    email      varchar(255)                NOT NULL,
    created_at timestamp with time zone    NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE organization (
    id         uuid                        NOT NULL,
    name       varchar(150)                NOT NULL,
    status     varchar(255)                NOT NULL,
    created_at timestamp with time zone    NOT NULL,
    CONSTRAINT pk_organization PRIMARY KEY (id),
    CONSTRAINT uq_organization_name UNIQUE (name)
);

CREATE TABLE org_member (
    user_id     uuid NOT NULL,
    org_id      uuid NOT NULL,
    member_role varchar(255) NOT NULL,
    invited_by  uuid,
    CONSTRAINT pk_org_member PRIMARY KEY (user_id),
    CONSTRAINT fk_org_member_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_org_member_org FOREIGN KEY (org_id) REFERENCES organization (id)
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
