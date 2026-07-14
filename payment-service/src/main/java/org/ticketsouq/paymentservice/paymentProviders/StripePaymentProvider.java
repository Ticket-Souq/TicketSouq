package org.ticketsouq.paymentservice.paymentProviders;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.paymentservice.dto.PaymentRequest;
import org.ticketsouq.paymentservice.dto.PaymentResponse;
import org.ticketsouq.paymentservice.enums.PaymentProviderEnum;
import org.ticketsouq.paymentservice.enums.PaymentStatus;
import org.ticketsouq.paymentservice.exception.PaymentException;
import org.ticketsouq.paymentservice.model.PaymentModel;
import org.ticketsouq.paymentservice.repository.PaymentRepository;
import org.ticketsouq.sharedmodule.GeneralExceptions.ResourceNotFoundException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

@Service
@Profile("STRIPE")
public class StripePaymentProvider implements PaymentProvider {

    private final PaymentRepository paymentRepository;

    public StripePaymentProvider(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Override
    @Transactional
    public PaymentResponse pay(PaymentRequest request) {
        long amountInSmallestUnit = convertToSmallestCurrencyUnit(request.amount(), request.currency());

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountInSmallestUnit)
                .setCurrency(request.currency().toLowerCase())
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .build())
                .build();

        try {
            PaymentIntent intent = PaymentIntent.create(params);

            PaymentModel payment = PaymentModel.builder()
                    .reservationID(request.reservationID())
                    .customerID(request.customerID())
                    .amount(request.amount())
                    .currency(request.currency())
                    .paymentStatus(PaymentStatus.PENDING)
                    .paymentProvider(PaymentProviderEnum.STRIPE_PAYMENT)
                    .stripePaymentIntentId(intent.getId())
                    .transactionRef(intent.getId())
                    .build();

            paymentRepository.save(payment);

            return new PaymentResponse(
                    intent.getClientSecret(),
                    payment.getId(),
                    PaymentStatus.PENDING,
                    "Payment initiated. Complete payment on the frontend."
            );
        } catch (StripeException e) {
            throw new PaymentException("Failed to create Stripe PaymentIntent: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPayment(java.util.UUID paymentId) {
        PaymentModel payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        return new PaymentResponse(
                null,
                payment.getId(),
                payment.getPaymentStatus(),
                "Payment retrieved successfully"
        );
    }

    @Override
    @Transactional
    public void refund(java.util.UUID paymentId) {
        PaymentModel payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        if (payment.getPaymentStatus() != PaymentStatus.SUCCESS) {
            throw new PaymentException("Cannot refund a payment that is not in SUCCESS status. Current status: " + payment.getPaymentStatus());
        }

        try {
            RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(payment.getStripePaymentIntentId())
                    .build();

            Refund.create(params);

            payment.setPaymentStatus(PaymentStatus.REFUNDED);
            paymentRepository.save(payment);
        } catch (StripeException e) {
            throw new PaymentException("Refund failed for payment " + paymentId + ": " + e.getMessage(), e);
        }
    }

    private long convertToSmallestCurrencyUnit(BigDecimal amount, String currencyCode) {
        Currency currency = Currency.getInstance(currencyCode);
        int fractionDigits = currency.getDefaultFractionDigits();
        return amount.multiply(BigDecimal.valueOf(Math.pow(10, fractionDigits)))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }
}
