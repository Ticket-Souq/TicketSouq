package org.ticketsouq.paymentservice.service;

import com.stripe.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.ticketsouq.paymentservice.dto.PaymentRequest;
import org.ticketsouq.paymentservice.dto.PaymentResponse;
import org.ticketsouq.paymentservice.enums.PaymentStatus;
import org.ticketsouq.paymentservice.exception.PaymentException;
import org.ticketsouq.paymentservice.model.PaymentModel;
import org.ticketsouq.paymentservice.paymentProviders.PaymentProvider;
import org.ticketsouq.paymentservice.repository.PaymentRepository;
import org.ticketsouq.sharedmodule.GeneralExceptions.ResourceNotFoundException;
import org.ticketsouq.sharedmodule.PaymentService.events.PaymentFailedEvent;
import org.ticketsouq.sharedmodule.PaymentService.events.PaymentSuccessEvent;
import org.ticketsouq.sharedmodule.PaymentService.events.RefundCompletedEvent;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentProvider paymentProvider;
    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final PlatformTransactionManager transactionManager;


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

    public void handlePaymentSucceeded(String stripePaymentIntentId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.execute(status -> {
            PaymentModel payment = findPaymentByStripeId(stripePaymentIntentId);
            if (payment.getPaymentStatus() == PaymentStatus.SUCCESS) {
                log.info("Payment already in SUCCESS status, skipping duplicate event for Stripe ID: {}", stripePaymentIntentId);
                return null;
            }
            payment.setPaymentStatus(PaymentStatus.SUCCESS);
            paymentRepository.save(payment);
            PaymentSuccessEvent event = new PaymentSuccessEvent(
                UUID.randomUUID(),
                payment.getCustomerID(),
                payment.getReservationID(),
                payment.getAmount()
            );
            applicationEventPublisher.publishEvent(event);
            return null;
        });
    }

    public void handlePaymentFailed(String stripePaymentIntentId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.execute(status -> {
            PaymentModel payment = findPaymentByStripeId(stripePaymentIntentId);
            if (payment.getPaymentStatus() == PaymentStatus.FAILED) {
                log.info("Payment already in FAILED status, skipping duplicate event for Stripe ID: {}", stripePaymentIntentId);
                return null;
            }
            payment.setPaymentStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            PaymentFailedEvent event = new PaymentFailedEvent(
                UUID.randomUUID(),
                payment.getCustomerID(),
                payment.getReservationID(),
                payment.getAmount()
            );
            applicationEventPublisher.publishEvent(event);
            return null;
        });
        throw new PaymentException("Payment failed for reservation");
    }

    public void handleRefundCompleted(String stripePaymentIntentId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.execute(status -> {
            PaymentModel payment = findPaymentByStripeId(stripePaymentIntentId);
            if (payment.getPaymentStatus() == PaymentStatus.REFUNDED) {
                log.info("Payment already in REFUNDED status, skipping duplicate event for Stripe ID: {}", stripePaymentIntentId);
                return null;
            }
            payment.setPaymentStatus(PaymentStatus.REFUNDED);
            paymentRepository.save(payment);
            RefundCompletedEvent event = new RefundCompletedEvent(
                UUID.randomUUID(),
                payment.getCustomerID(),
                payment.getReservationID(),
                payment.getAmount()
            );
            applicationEventPublisher.publishEvent(event);
            return null;
        });
    }

    private PaymentModel findPaymentByStripeId(String stripePaymentIntentId) {
        return paymentRepository.findByStripePaymentIntentId(stripePaymentIntentId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment with Stripe ID", stripePaymentIntentId));
    }
}
