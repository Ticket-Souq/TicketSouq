package org.ticketsouq.paymentservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.ticketsouq.paymentservice.paymentProviders.MockPaymentProvider;
import org.ticketsouq.paymentservice.paymentProviders.MockPayoutProvider;
import org.ticketsouq.paymentservice.paymentProviders.PaymentProvider;
import org.ticketsouq.paymentservice.paymentProviders.PayoutProvider;
import org.ticketsouq.paymentservice.paymentProviders.StripePaymentProvider;
import org.ticketsouq.paymentservice.paymentProviders.StripePayoutProvider;
import org.ticketsouq.paymentservice.repository.PaymentRepository;

@Configuration
public class PaymentConfig {

    @Bean
    @ConditionalOnProperty(name = "payment.provider", havingValue = "stripe")
    public PaymentProvider stripePaymentProvider(PaymentRepository paymentRepository) {
        return new StripePaymentProvider(paymentRepository);
    }

    @Bean
    @ConditionalOnProperty(name = "payment.provider", havingValue = "mock", matchIfMissing = true)
    public PaymentProvider mockPaymentProvider(
        PaymentRepository paymentRepository,
        @Value("${payment.mock.success-rate:100}") int successRate) {
        return new MockPaymentProvider(paymentRepository, successRate);
    }

    @Bean
    @ConditionalOnProperty(name = "payout.provider", havingValue = "stripe")
    public PayoutProvider stripePayoutProvider() {
        return new StripePayoutProvider();
    }

    @Bean
    @ConditionalOnProperty(name = "payout.provider", havingValue = "mock", matchIfMissing = true)
    public PayoutProvider mockPayoutProvider(
            @Value("${payout.mock.success-rate:100}") int successRate) {
        return new MockPayoutProvider(successRate);
    }
}
