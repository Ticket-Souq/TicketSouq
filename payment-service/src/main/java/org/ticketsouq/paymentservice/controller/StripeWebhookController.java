package org.ticketsouq.paymentservice.controller;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ticketsouq.paymentservice.service.PaymentService;

@Slf4j
@RestController
@RequestMapping("api/v1/webhook")
public class StripeWebhookController {

    private final PaymentService paymentService;
    private final String webhookSecret;

    public StripeWebhookController(PaymentService paymentService, String stripeWebhookSecret) {
        this.paymentService = paymentService;
        this.webhookSecret = stripeWebhookSecret;
    }

    @PostMapping("/stripe")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Invalid Stripe webhook signature: {}", e.getMessage());
            return ResponseEntity.status(400).body("Invalid signature");
        }

        String eventType = event.getType();
        log.info("Received Stripe webhook event: {}", eventType);

        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
        if (!dataObjectDeserializer.getObject().isPresent()) {
            log.warn("Unable to deserialize event data for event: {}", eventType);
            return ResponseEntity.ok("Event data not deserializable");
        }

        StripeObject stripeObject = dataObjectDeserializer.getObject().get();

        switch (eventType) {
            case "payment_intent.succeeded" -> {
                PaymentIntent paymentIntent = (PaymentIntent) stripeObject;
                log.info("PaymentIntent succeeded: {}", paymentIntent.getId());
                paymentService.handlePaymentSucceeded(paymentIntent.getId());
            }
            case "payment_intent.payment_failed" -> {
                PaymentIntent paymentIntent = (PaymentIntent) stripeObject;
                log.info("PaymentIntent failed: {}", paymentIntent.getId());
                paymentService.handlePaymentFailed(paymentIntent.getId());
            }
            case "charge.refunded" -> {
                com.stripe.model.Charge charge = (com.stripe.model.Charge) stripeObject;
                String paymentIntentId = charge.getPaymentIntent();
                log.info("Charge refunded for PaymentIntent: {}", paymentIntentId);
                if (paymentIntentId != null) {
                    paymentService.handleRefundCompleted(paymentIntentId);
                }
            }
            default -> log.info("Unhandled Stripe event type: {}", eventType);
        }

        return ResponseEntity.ok("Webhook received");
    }
}
