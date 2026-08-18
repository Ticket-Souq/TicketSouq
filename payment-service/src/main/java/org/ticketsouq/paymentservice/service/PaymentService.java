package org.ticketsouq.paymentservice.service;

import com.stripe.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.ticketsouq.outbox.service.OutboxWriter;
import org.ticketsouq.paymentservice.enums.PaymentStatus;
import org.ticketsouq.paymentservice.model.PaymentModel;
import org.ticketsouq.paymentservice.paymentProviders.PaymentProvider;
import org.ticketsouq.paymentservice.repository.PaymentRepository;
import org.ticketsouq.sharedmodule.GeneralExceptions.ResourceNotFoundException;
import org.ticketsouq.sharedmodule.PaymentService.events.PaymentFailedEvent;
import org.ticketsouq.sharedmodule.PaymentService.events.PaymentSuccessEvent;
import org.ticketsouq.sharedmodule.PaymentService.events.RefundCompletedEvent;
import org.ticketsouq.sharedmodule.ReservationService.events.SagaPaymentReplyEvent;

import java.util.UUID;

import org.ticketsouq.paymentservice.metrics.PaymentMetrics;

import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.PAYMENT_FAILED;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.PAYMENT_REFUNDED;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.PAYMENT_SUCCESS;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.SAGA_PAYMENT_REPLY;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OutboxWriter outboxWriter;
    private final PlatformTransactionManager transactionManager;
    private final PaymentProvider paymentProvider;
    private final PaymentMetrics paymentMetrics;

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
        long start = System.currentTimeMillis();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        PaymentModel updatedPayment = tx.execute(status -> {
            PaymentModel currentPayment = findPaymentByStripeId(stripePaymentIntentId);
            if (currentPayment.getPaymentStatus() == PaymentStatus.SUCCESS) {
                log.info("Payment already in SUCCESS status, skipping duplicate event for Stripe ID: {}", stripePaymentIntentId);
                return currentPayment;
            }
            currentPayment.setPaymentStatus(PaymentStatus.SUCCESS);
            paymentRepository.save(currentPayment);
            PaymentSuccessEvent event = new PaymentSuccessEvent(
                UUID.randomUUID(),
                currentPayment.getCustomerID(),
                currentPayment.getReservationID(),
                currentPayment.getAmount()
            );
            outboxWriter.save(event, PAYMENT_SUCCESS, currentPayment.getCustomerID().toString());
            return currentPayment;
        });
        paymentMetrics.recordPaymentProcessing(System.currentTimeMillis() - start);
        if (updatedPayment != null) {
            paymentMetrics.recordPaymentSuccess(updatedPayment.getAmount().doubleValue());
            publishSagaReply(updatedPayment, true, null);
        }
    }

    public void handlePaymentFailed(String stripePaymentIntentId) {
        long start = System.currentTimeMillis();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        PaymentModel updatedPayment = tx.execute(status -> {
            PaymentModel currentPayment = findPaymentByStripeId(stripePaymentIntentId);
            if (currentPayment.getPaymentStatus() == PaymentStatus.FAILED) {
                log.info("Payment already in FAILED status, skipping duplicate event for Stripe ID: {}", stripePaymentIntentId);
                return currentPayment;
            }
            currentPayment.setPaymentStatus(PaymentStatus.FAILED);
            paymentRepository.save(currentPayment);
            PaymentFailedEvent event = new PaymentFailedEvent(
                UUID.randomUUID(),
                currentPayment.getCustomerID(),
                currentPayment.getReservationID(),
                currentPayment.getAmount()
            );
            outboxWriter.save(event, PAYMENT_FAILED, currentPayment.getCustomerID().toString());
            return currentPayment;
        });
        paymentMetrics.recordPaymentProcessing(System.currentTimeMillis() - start);
        if (updatedPayment != null) {
            paymentMetrics.recordPaymentFailed(updatedPayment.getAmount().doubleValue());
            publishSagaReply(updatedPayment, false, "Payment failed");
        }
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
            paymentMetrics.recordPaymentRefund(payment.getAmount().doubleValue());
            RefundCompletedEvent event = new RefundCompletedEvent(
                UUID.randomUUID(),
                payment.getCustomerID(),
                payment.getReservationID(),
                payment.getAmount()
            );
            outboxWriter.save(event, PAYMENT_REFUNDED, payment.getCustomerID().toString());
            return null;
        });
    }

    private PaymentModel findPaymentByStripeId(String stripePaymentIntentId) {
        return paymentRepository.findByStripePaymentIntentId(stripePaymentIntentId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment with Stripe ID", stripePaymentIntentId));
    }

    private void publishSagaReply(PaymentModel payment, boolean success, String failReason) {
        if (payment.getReservationID() == null) {
            log.warn("Skipping saga reply for paymentId={} because reservationId is null", payment.getId());
            return;
        }

        SagaPaymentReplyEvent reply = new SagaPaymentReplyEvent(
            payment.getReservationID(),
            payment.getId(),
            success,
            failReason
        );
        outboxWriter.save(reply, SAGA_PAYMENT_REPLY, payment.getReservationID().toString());
        log.info("Sent SagaPaymentReplyEvent for reservationId={}, paymentId={}, success={}",
            payment.getReservationID(), payment.getId(), success);
    }
}
