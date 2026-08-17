package org.ticketsouq.paymentservice.listener;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ticketsouq.paymentservice.enums.PaymentStatus;
import org.ticketsouq.paymentservice.model.PaymentModel;
import org.ticketsouq.paymentservice.paymentProviders.PaymentProvider;
import org.ticketsouq.paymentservice.repository.PaymentRepository;
import org.ticketsouq.sharedmodule.ReservationService.events.SagaPaymentCompensateCommand;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SagaPaymentCompensateConsumerTest {

    private static final UUID PAYMENT_ID = UUID.randomUUID();
    private static final UUID RESERVATION_ID = UUID.randomUUID();

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentProvider paymentProvider;

    @Test
    void compensationRefundsOnlySuccessPayments() {
        PaymentModel payment = payment(PaymentStatus.SUCCESS);
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        SagaPaymentCompensateConsumer consumer = new SagaPaymentCompensateConsumer(paymentRepository, paymentProvider);

        consumer.handleSagaPaymentCompensate(command());

        verify(paymentProvider).refund(PAYMENT_ID);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(paymentRepository).save(payment);
    }

    @Test
    void compensationSkipsAlreadyRefundedPayments() {
        PaymentModel payment = payment(PaymentStatus.REFUNDED);
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        SagaPaymentCompensateConsumer consumer = new SagaPaymentCompensateConsumer(paymentRepository, paymentProvider);

        consumer.handleSagaPaymentCompensate(command());

        verify(paymentProvider, never()).refund(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void compensationSkipsFailedPayments() {
        PaymentModel payment = payment(PaymentStatus.FAILED);
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        SagaPaymentCompensateConsumer consumer = new SagaPaymentCompensateConsumer(paymentRepository, paymentProvider);

        consumer.handleSagaPaymentCompensate(command());

        verify(paymentProvider, never()).refund(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void compensationSkipsPendingPayments() {
        PaymentModel payment = payment(PaymentStatus.PENDING);
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        SagaPaymentCompensateConsumer consumer = new SagaPaymentCompensateConsumer(paymentRepository, paymentProvider);

        consumer.handleSagaPaymentCompensate(command());

        verify(paymentProvider, never()).refund(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void compensationSkipsWhenPaymentNotFound() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());
        SagaPaymentCompensateConsumer consumer = new SagaPaymentCompensateConsumer(paymentRepository, paymentProvider);

        consumer.handleSagaPaymentCompensate(command());

        verify(paymentProvider, never()).refund(any());
        verify(paymentRepository, never()).save(any());
    }

    private SagaPaymentCompensateCommand command() {
        return new SagaPaymentCompensateCommand(RESERVATION_ID, PAYMENT_ID);
    }

    private PaymentModel payment(PaymentStatus status) {
        return PaymentModel.builder()
            .id(PAYMENT_ID)
            .reservationID(RESERVATION_ID)
            .customerID(UUID.randomUUID())
            .amount(new BigDecimal("100.00"))
            .paymentStatus(status)
            .stripePaymentIntentId("pi_123")
            .build();
    }
}