package org.ticketsouq.paymentservice.service;

import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.paymentservice.dto.PaymentRequest;
import org.ticketsouq.paymentservice.dto.PaymentResponse;
import org.ticketsouq.paymentservice.enums.PaymentStatus;
import org.ticketsouq.paymentservice.model.PaymentModel;
import org.ticketsouq.paymentservice.paymentProviders.PaymentProvider;
import org.ticketsouq.paymentservice.repository.PaymentRepository;
import org.ticketsouq.sharedmodule.GeneralExceptions.ResourceNotFoundException;
import org.ticketsouq.sharedmodule.PaymentService.events.PaymentSuccessEvent;
import org.ticketsouq.sharedmodule.PaymentService.events.RefundCompletedEvent;

import java.util.UUID;

import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.PAYMENT_SUCCESS;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.PAYMENT_REFUNDED;

@Service
public class PaymentService {

    private final PaymentProvider paymentProvider;
    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentService(PaymentProvider paymentProvider,
                          PaymentRepository paymentRepository,
                          KafkaTemplate<String, Object> kafkaTemplate) {
        this.paymentProvider = paymentProvider;
        this.paymentRepository = paymentRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

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

    @Transactional
    public void handlePaymentSucceeded(String stripePaymentIntentId) {
        PaymentModel payment = paymentRepository.findByStripePaymentIntentId(stripePaymentIntentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment with Stripe ID", stripePaymentIntentId));

        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        paymentRepository.save(payment);

        PaymentSuccessEvent event = new PaymentSuccessEvent(
                UUID.randomUUID(),
                payment.getCustomerID(),
                payment.getReservationID(),
                payment.getAmount()
        );
        kafkaTemplate.send(PAYMENT_SUCCESS, payment.getCustomerID().toString(), event);
    }

    @Transactional
    public void handlePaymentFailed(String stripePaymentIntentId) {
        PaymentModel payment = paymentRepository.findByStripePaymentIntentId(stripePaymentIntentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment with Stripe ID", stripePaymentIntentId));

        payment.setPaymentStatus(PaymentStatus.FAILED);
        paymentRepository.save(payment);
    }

    @Transactional
    public void handleRefundCompleted(String stripePaymentIntentId) {
        PaymentModel payment = paymentRepository.findByStripePaymentIntentId(stripePaymentIntentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment with Stripe ID", stripePaymentIntentId));

        payment.setPaymentStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(payment);

        RefundCompletedEvent event = new RefundCompletedEvent(
                UUID.randomUUID(),
                payment.getCustomerID(),
                payment.getReservationID(),
                payment.getAmount()
        );
        kafkaTemplate.send(PAYMENT_REFUNDED, payment.getCustomerID().toString(), event);
    }
}
