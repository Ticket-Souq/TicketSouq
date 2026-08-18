-- V1: initial schema for audit-service (audit_db)

CREATE TABLE audit_logs (
    id         uuid                        NOT NULL,
    action     varchar(100)                NOT NULL,
    made_by_id uuid                        NOT NULL,
    reason     text,
    made_at    timestamp with time zone    NOT NULL,
    CONSTRAINT pk_audit_logs PRIMARY KEY (id)
);
CREATE INDEX idx_audit_made_by ON audit_logs (made_by_id);
CREATE INDEX idx_audit_action ON audit_logs (action);
CREATE INDEX idx_audit_made_at ON audit_logs (made_at);
