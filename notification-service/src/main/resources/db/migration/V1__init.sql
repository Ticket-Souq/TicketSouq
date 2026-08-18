-- V1: initial schema for notification-service (notification_db)

CREATE TABLE notifications (
    id         uuid                        NOT NULL,
    user_id    uuid                        NOT NULL,
    event_id   bigint,
    title      varchar(150)                NOT NULL,
    message    text                        NOT NULL,
    type       varchar(255)                NOT NULL,
    is_read    boolean                     NOT NULL,
    created_at timestamp                   NOT NULL,
    CONSTRAINT pk_notifications PRIMARY KEY (id)
);
CREATE INDEX idx_user_read ON notifications (user_id, is_read);

CREATE TABLE email_jobs (
    id              uuid                        NOT NULL,
    message_id      uuid                        NOT NULL,
    recipient       varchar(255)                NOT NULL,
    template        varchar(255)                NOT NULL,
    variables_json  text                        NOT NULL,
    status          varchar(255)                NOT NULL,
    retry_count     integer                     NOT NULL,
    last_attempt_at timestamp,
    created_at      timestamp                   NOT NULL,
    CONSTRAINT pk_email_jobs PRIMARY KEY (id),
    CONSTRAINT uq_email_jobs_message_id UNIQUE (message_id)
);
CREATE INDEX idx_email_job_status ON email_jobs (status);

CREATE TABLE user_email_projection (
    user_id uuid NOT NULL,
    email   varchar(255) NOT NULL,
    CONSTRAINT pk_user_email_projection PRIMARY KEY (user_id),
    CONSTRAINT uq_user_email_projection_email UNIQUE (email)
);
