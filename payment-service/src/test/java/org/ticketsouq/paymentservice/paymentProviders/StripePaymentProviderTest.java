package org.ticketsouq.paymentservice.paymentProviders;

import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ticketsouq.paymentservice.dto.PaymentRequest;
import org.ticketsouq.paymentservice.dto.PaymentResponse;
import org.ticketsouq.paymentservice.enums.PaymentStatus;
import org.ticketsouq.paymentservice.model.PaymentModel;
import org.ticketsouq.paymentservice.repository.PaymentRepository;
import org.ticketsouq.sharedmodule.PaymentService.exception.PaymentException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripePaymentProviderTest {

    private static final UUID RESERVATION_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();

    @Mock
    private PaymentRepository paymentRepository;

    @Test
    void pay_createsPaymentIntentWithIdempotencyKeyMetadataAndPersistsPendingPayment() {
        PaymentIntent intent = new PaymentIntent();
        intent.setId("pi_abc");
        intent.setClientSecret("pi_secret_abc");

        try (MockedStatic<PaymentIntent> mocked = mockStatic(PaymentIntent.class)) {
            mocked.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
                .thenReturn(intent);

            StripePaymentProvider provider = new StripePaymentProvider(paymentRepository);
            PaymentResponse response = provider.pay(request());

            ArgumentCaptor<PaymentIntentCreateParams> paramsCaptor = ArgumentCaptor.forClass(PaymentIntentCreateParams.class);
            ArgumentCaptor<RequestOptions> optionsCaptor = ArgumentCaptor.forClass(RequestOptions.class);
            mocked.verify(() -> PaymentIntent.create(paramsCaptor.capture(), optionsCaptor.capture()));

            assertThat(optionsCaptor.getValue().getIdempotencyKey())
                .isEqualTo("reservation-payment-" + RESERVATION_ID);

            PaymentIntentCreateParams params = paramsCaptor.getValue();
            assertThat(params.getAmount()).isEqualTo(10000L); // EGP 100.00 in smallest unit
            assertThat(params.getCurrency()).isEqualTo("egp");
            assertThat(params.getMetadata())
                .containsEntry("reservationId", RESERVATION_ID.toString())
                .containsEntry("customerId", CUSTOMER_ID.toString())
                .containsEntry("eventId", EVENT_ID.toString());

            ArgumentCaptor<PaymentModel> modelCaptor = ArgumentCaptor.forClass(PaymentModel.class);
            verify(paymentRepository).save(modelCaptor.capture());
            PaymentModel saved = modelCaptor.getValue();
            assertThat(saved.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(saved.getStripePaymentIntentId()).isEqualTo("pi_abc");
            assertThat(saved.getClientSecret()).isEqualTo("pi_secret_abc");
            assertThat(saved.getReservationID()).isEqualTo(RESERVATION_ID);
            assertThat(saved.getCustomerID()).isEqualTo(CUSTOMER_ID);

            assertThat(response.clientSecret()).isEqualTo("pi_secret_abc");
            assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        }
    }

    @Test
    void getPayment_neverExposesClientSecret() {
        PaymentModel payment = paymentModel(PaymentStatus.SUCCESS, "pi_secret_abc");
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        StripePaymentProvider provider = new StripePaymentProvider(paymentRepository);
        PaymentResponse response = provider.getPayment(payment.getId());

        assertThat(response.clientSecret()).isNull();
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void refund_refundsSuccessPaymentAndMarksRefunded() {
        PaymentModel payment = paymentModel(PaymentStatus.SUCCESS, null);
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        try (MockedStatic<Refund> mocked = mockStatic(Refund.class)) {
            mocked.when(() -> Refund.create(any(RefundCreateParams.class))).thenReturn(new Refund());

            StripePaymentProvider provider = new StripePaymentProvider(paymentRepository);
            provider.refund(payment.getId());

            ArgumentCaptor<RefundCreateParams> captor = ArgumentCaptor.forClass(RefundCreateParams.class);
            mocked.verify(() -> Refund.create(captor.capture()));
            assertThat(captor.getValue().getPaymentIntent()).isEqualTo("pi_abc");

            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
            verify(paymentRepository).save(payment);
        }
    }

    @Test
    void refund_throwsForNonSuccessPaymentAndDoesNotCallStripe() {
        PaymentModel payment = paymentModel(PaymentStatus.PENDING, null);
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        try (MockedStatic<Refund> mocked = mockStatic(Refund.class)) {
            StripePaymentProvider provider = new StripePaymentProvider(paymentRepository);

            assertThatThrownBy(() -> provider.refund(payment.getId()))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("not in SUCCESS status");

            mocked.verify(() -> Refund.create(any(RefundCreateParams.class)), never());
        }
    }

    private PaymentModel paymentModel(PaymentStatus status, String clientSecret) {
        return PaymentModel.builder()
            .id(UUID.randomUUID())
            .reservationID(RESERVATION_ID)
            .customerID(CUSTOMER_ID)
            .amount(new BigDecimal("100.00"))
            .paymentStatus(status)
            .stripePaymentIntentId("pi_abc")
            .clientSecret(clientSecret)
            .build();
    }

    private PaymentRequest request() {
        return new PaymentRequest(RESERVATION_ID, CUSTOMER_ID, EVENT_ID, new BigDecimal("100.00"));
    }
}