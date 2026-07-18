package org.ticketsouq.paymentservice.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.ticketsouq.paymentservice.paymentProviders.MockPaymentProvider;
import org.ticketsouq.paymentservice.paymentProviders.PaymentProvider;
import org.ticketsouq.paymentservice.paymentProviders.StripePaymentProvider;
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
    public PaymentProvider mockPaymentProvider() {
        return new MockPaymentProvider();
    }
}
