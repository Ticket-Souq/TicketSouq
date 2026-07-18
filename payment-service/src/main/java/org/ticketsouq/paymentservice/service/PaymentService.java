package org.ticketsouq.paymentservice.service;

import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.paymentservice.dto.PaymentRequest;
import org.ticketsouq.paymentservice.dto.PaymentResponse;
import org.ticketsouq.paymentservice.enums.PaymentStatus;
import org.ticketsouq.paymentservice.exception.PaymentException;
import org.ticketsouq.paymentservice.model.PaymentModel;
import org.ticketsouq.paymentservice.paymentProviders.PaymentProvider;
import org.ticketsouq.paymentservice.repository.PaymentRepository;
import org.ticketsouq.sharedmodule.GeneralExceptions.ResourceNotFoundException;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentProvider paymentProvider;
    private final PaymentRepository paymentRepository;
    private final PaymentEventPublisher eventPublisher;

    public ResponseEntity<PaymentResponse> pay(PaymentRequest request) {
        PaymentResponse paymentResponse = paymentProvider.pay(request);
        return ResponseEntity.ok(paymentResponse);
    }

    public ResponseEntity<PaymentResponse> getPayment(UUID paymentId) {
        PaymentResponse paymentResponse = paymentProvider.getPayment(paymentId);
        return ResponseEntity.ok(paymentResponse);
    }

    public void refund(UUID paymentId) {
        paymentProvider.refund(paymentId);
    }

    public void handleWebhookEvent(Event event) {
        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
        if (!dataObjectDeserializer.getObject().isPresent()) {
            log.warn("Unable to deserialize event data for event: {}", event.getType());
            return;
        }

        StripeObject stripeObject = dataObjectDeserializer.getObject().get();

        switch (event.getType()) {
            case "payment_intent.succeeded" -> {
                PaymentIntent paymentIntent = (PaymentIntent) stripeObject;
                log.info("PaymentIntent succeeded: {}", paymentIntent.getId());
                handlePaymentSucceeded(paymentIntent.getId());
            }
            case "payment_intent.payment_failed" -> {
                PaymentIntent paymentIntent = (PaymentIntent) stripeObject;
                log.info("PaymentIntent failed: {}", paymentIntent.getId());
                handlePaymentFailed(paymentIntent.getId());
            }
            case "charge.refunded" -> {
                Charge charge = (Charge) stripeObject;
                String paymentIntentId = charge.getPaymentIntent();
                log.info("Charge refunded for PaymentIntent: {}", paymentIntentId);
                if (paymentIntentId != null) {
                    handleRefundCompleted(paymentIntentId);
                }
            }
            default -> log.info("Unhandled Stripe event type: {}", event.getType());
        }
    }

    @Transactional
    public void handlePaymentSucceeded(String stripePaymentIntentId) {
        PaymentModel payment = findPaymentByStripeId(stripePaymentIntentId);
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        paymentRepository.save(payment);
        eventPublisher.publishPaymentSuccess(payment);
    }

    @Transactional
    public void handlePaymentFailed(String stripePaymentIntentId) {
        PaymentModel payment = findPaymentByStripeId(stripePaymentIntentId);
        payment.setPaymentStatus(PaymentStatus.FAILED);
        paymentRepository.save(payment);
        eventPublisher.publishPaymentFailed(payment);
        throw new PaymentException("Payment failed for reservation: " + payment.getReservationID());
    }

    @Transactional
    public void handleRefundCompleted(String stripePaymentIntentId) {
        PaymentModel payment = findPaymentByStripeId(stripePaymentIntentId);
        payment.setPaymentStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(payment);
        eventPublisher.publishRefundCompleted(payment);
    }

    private PaymentModel findPaymentByStripeId(String stripePaymentIntentId) {
        return paymentRepository.findByStripePaymentIntentId(stripePaymentIntentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment with Stripe ID", stripePaymentIntentId));
    }
}
