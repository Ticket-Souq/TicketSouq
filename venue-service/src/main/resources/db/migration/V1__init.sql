-- V1: initial schema for venue-service (venue_db)

CREATE TABLE venue (
    id           uuid NOT NULL,
    organization varchar(255) NOT NULL,
    name         varchar(255) NOT NULL,
    address      varchar(255) NOT NULL,
    type         varchar(255),
    deleted      boolean NOT NULL,
    CONSTRAINT pk_venue PRIMARY KEY (id)
);

CREATE TABLE venue_templates (
    id       uuid NOT NULL,
    venue_id uuid,
    layout   jsonb,
    deleted  boolean NOT NULL,
    CONSTRAINT pk_venue_templates PRIMARY KEY (id),
    CONSTRAINT fk_venue_templates_venue FOREIGN KEY (venue_id) REFERENCES venue (id)
);
