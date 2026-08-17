package org.ticketsouq.paymentservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.ticketsouq.outbox.service.OutboxWriter;
import org.ticketsouq.paymentservice.enums.PaymentStatus;
import org.ticketsouq.paymentservice.metrics.PaymentMetrics;
import org.ticketsouq.paymentservice.model.PaymentModel;
import org.ticketsouq.paymentservice.paymentProviders.PaymentProvider;
import org.ticketsouq.paymentservice.repository.PaymentRepository;
import org.ticketsouq.sharedmodule.PaymentService.events.PaymentFailedEvent;
import org.ticketsouq.sharedmodule.PaymentService.events.PaymentSuccessEvent;
import org.ticketsouq.sharedmodule.PaymentService.events.RefundCompletedEvent;
import org.ticketsouq.sharedmodule.ReservationService.events.SagaPaymentReplyEvent;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.PAYMENT_FAILED;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.PAYMENT_REFUNDED;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.PAYMENT_SUCCESS;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.SAGA_PAYMENT_REPLY;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final String STRIPE_INTENT_ID = "pi_123";
    private static final UUID RESERVATION_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();

    @Mock private PaymentRepository paymentRepository;
    @Mock private OutboxWriter outboxWriter;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private PaymentProvider paymentProvider;
    @Mock private PaymentMetrics paymentMetrics;
    @Mock private TransactionStatus transactionStatus;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        paymentService = new PaymentService(paymentRepository, outboxWriter, transactionManager, paymentProvider, paymentMetrics);
    }

    private PaymentModel payment(PaymentStatus status) {
        return PaymentModel.builder()
            .id(UUID.randomUUID())
            .reservationID(RESERVATION_ID)
            .customerID(CUSTOMER_ID)
            .amount(new BigDecimal("100.00"))
            .paymentStatus(status)
            .stripePaymentIntentId(STRIPE_INTENT_ID)
            .build();
    }

    @Test
    void webhookSuccess_updatesPaymentToSuccess_andWritesSagaReplyOutboxEvent() {
        PaymentModel payment = payment(PaymentStatus.PENDING);
        when(paymentRepository.findByStripePaymentIntentId(STRIPE_INTENT_ID)).thenReturn(Optional.of(payment));

        paymentService.handlePaymentSucceeded(STRIPE_INTENT_ID);

        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(paymentRepository).save(payment);
        verify(outboxWriter).save(any(PaymentSuccessEvent.class), eq(PAYMENT_SUCCESS), eq(CUSTOMER_ID.toString()));

        ArgumentCaptor<SagaPaymentReplyEvent> captor = ArgumentCaptor.forClass(SagaPaymentReplyEvent.class);
        verify(outboxWriter).save(captor.capture(), eq(SAGA_PAYMENT_REPLY), eq(RESERVATION_ID.toString()));
        assertThat(captor.getValue().success()).isTrue();
        assertThat(captor.getValue().reservationId()).isEqualTo(RESERVATION_ID);
        assertThat(captor.getValue().paymentId()).isEqualTo(payment.getId());
        assertThat(captor.getValue().failReason()).isNull();
    }

    @Test
    void webhookSuccess_doesNotDuplicateStatusButRepublishesSagaReply() {
        PaymentModel payment = payment(PaymentStatus.SUCCESS);
        when(paymentRepository.findByStripePaymentIntentId(STRIPE_INTENT_ID)).thenReturn(Optional.of(payment));

        paymentService.handlePaymentSucceeded(STRIPE_INTENT_ID);

        verify(paymentRepository, never()).save(payment);
        verify(outboxWriter, never()).save(any(PaymentSuccessEvent.class), eq(PAYMENT_SUCCESS), any());
        verify(outboxWriter).save(any(SagaPaymentReplyEvent.class), eq(SAGA_PAYMENT_REPLY), eq(RESERVATION_ID.toString()));
    }

    @Test
    void webhookFailure_updatesPaymentToFailed_andWritesFailedSagaReply() {
        PaymentModel payment = payment(PaymentStatus.PENDING);
        when(paymentRepository.findByStripePaymentIntentId(STRIPE_INTENT_ID)).thenReturn(Optional.of(payment));

        paymentService.handlePaymentFailed(STRIPE_INTENT_ID);

        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentRepository).save(payment);
        verify(outboxWriter).save(any(PaymentFailedEvent.class), eq(PAYMENT_FAILED), eq(CUSTOMER_ID.toString()));

        ArgumentCaptor<SagaPaymentReplyEvent> captor = ArgumentCaptor.forClass(SagaPaymentReplyEvent.class);
        verify(outboxWriter).save(captor.capture(), eq(SAGA_PAYMENT_REPLY), eq(RESERVATION_ID.toString()));
        assertThat(captor.getValue().success()).isFalse();
        assertThat(captor.getValue().failReason()).isEqualTo("Payment failed");
    }

    @Test
    void webhookFailure_doesNotDuplicateStatusButRepublishesSagaReply() {
        PaymentModel payment = payment(PaymentStatus.FAILED);
        when(paymentRepository.findByStripePaymentIntentId(STRIPE_INTENT_ID)).thenReturn(Optional.of(payment));

        paymentService.handlePaymentFailed(STRIPE_INTENT_ID);

        verify(paymentRepository, never()).save(payment);
        verify(outboxWriter, never()).save(any(PaymentFailedEvent.class), eq(PAYMENT_FAILED), any());
        verify(outboxWriter).save(any(SagaPaymentReplyEvent.class), eq(SAGA_PAYMENT_REPLY), eq(RESERVATION_ID.toString()));
    }

    @Test
    void webhookRefunded_marksPaymentRefunded_andWritesRefundEvent() {
        PaymentModel payment = payment(PaymentStatus.SUCCESS);
        when(paymentRepository.findByStripePaymentIntentId(STRIPE_INTENT_ID)).thenReturn(Optional.of(payment));

        paymentService.handleRefundCompleted(STRIPE_INTENT_ID);

        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(paymentRepository).save(payment);
        verify(outboxWriter).save(any(RefundCompletedEvent.class), eq(PAYMENT_REFUNDED), eq(CUSTOMER_ID.toString()));
    }

    @Test
    void webhookRefunded_skipsWhenAlreadyRefunded() {
        PaymentModel payment = payment(PaymentStatus.REFUNDED);
        when(paymentRepository.findByStripePaymentIntentId(STRIPE_INTENT_ID)).thenReturn(Optional.of(payment));

        paymentService.handleRefundCompleted(STRIPE_INTENT_ID);

        verify(paymentRepository, never()).save(payment);
        verify(outboxWriter, never()).save(any(RefundCompletedEvent.class), eq(PAYMENT_REFUNDED), any());
    }
}