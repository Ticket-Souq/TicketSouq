-- V3: add event_id to payment_model for payout aggregation
-- One payment per reservation, but many reservations per event → need event grouping for payout.

ALTER TABLE payment_model
    ADD COLUMN event_id uuid;

CREATE INDEX idx_payment_model_event_id
    ON payment_model (event_id);
