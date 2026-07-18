package org.ticketsouq.paymentservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.ticketsouq.paymentservice.model.PaymentModel;
import org.ticketsouq.sharedmodule.PaymentService.events.PaymentFailedEvent;
import org.ticketsouq.sharedmodule.PaymentService.events.PaymentSuccessEvent;
import org.ticketsouq.sharedmodule.PaymentService.events.RefundCompletedEvent;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public void publishPaymentSuccess(PaymentModel payment) {
        PaymentSuccessEvent event = new PaymentSuccessEvent(
                UUID.randomUUID(),
                payment.getCustomerID(),
                payment.getReservationID(),
                payment.getAmount()
        );
        applicationEventPublisher.publishEvent(event);
        log.info("Published PaymentSuccessEvent for payment {}", payment.getId());
    }

    public void publishPaymentFailed(PaymentModel payment) {
        PaymentFailedEvent event = new PaymentFailedEvent(
                UUID.randomUUID(),
                payment.getCustomerID(),
                payment.getReservationID(),
                payment.getAmount()
        );
        applicationEventPublisher.publishEvent(event);
        log.info("Published PaymentFailedEvent for payment {}", payment.getId());
    }

    public void publishRefundCompleted(PaymentModel payment) {
        RefundCompletedEvent event = new RefundCompletedEvent(
                UUID.randomUUID(),
                payment.getCustomerID(),
                payment.getReservationID(),
                payment.getAmount()
        );
        applicationEventPublisher.publishEvent(event);
        log.info("Published RefundCompletedEvent for payment {}", payment.getId());
    }
}
