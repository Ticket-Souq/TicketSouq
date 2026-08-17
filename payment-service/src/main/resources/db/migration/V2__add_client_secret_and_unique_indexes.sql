-- V2: add client_secret and harden payment-service (payment_db)
-- Stores the Stripe PaymentIntent client secret so the frontend can mount Payment Elements
-- and confirm the payment. Unique indexes guarantee at most one payment per reservation and
-- per Stripe PaymentIntent, matching the idempotent saga consumer behaviour.

ALTER TABLE payment_model
    ADD COLUMN client_secret varchar(255);

CREATE UNIQUE INDEX uq_payment_model_reservationid
    ON payment_model (reservationid);

CREATE UNIQUE INDEX uq_payment_model_stripe_payment_intent_id
    ON payment_model (stripe_payment_intent_id);
