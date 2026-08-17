-- V2: add client_secret to payment-service (payment_db)
-- Stores the Stripe PaymentIntent client secret so the frontend can confirm the payment.

ALTER TABLE payment_model
    ADD COLUMN client_secret varchar(255);