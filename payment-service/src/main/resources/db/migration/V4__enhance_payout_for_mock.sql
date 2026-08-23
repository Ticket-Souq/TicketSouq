-- V4: enhance payout for mock payouts (one payout per event, 0% fee)
-- Converts stripe-only transfer column to generic provider_transfer_id,
-- adds event grouping, net amount, failure handling and idempotency.

ALTER TABLE payout
    ADD COLUMN event_id uuid,
    ADD COLUMN organization varchar(255),
    ADD COLUMN net_amount numeric(38, 2),
    ADD COLUMN provider_transfer_id varchar(255),
    ADD COLUMN failure_reason varchar(1024),
    ADD COLUMN retry_count integer DEFAULT 0;

-- Keep stripe_transfer_id for backwards compat, but new code writes provider_transfer_id.
-- Backfill net_amount = amount where null (0% fee).
UPDATE payout SET net_amount = amount WHERE net_amount IS NULL;

-- One payout per event — idempotency for event.payout-released consumer
CREATE UNIQUE INDEX uq_payout_event_id
    ON payout (event_id);

CREATE INDEX idx_payout_organizer_id
    ON payout (organizer_id);

CREATE INDEX idx_payout_status
    ON payout (status);
