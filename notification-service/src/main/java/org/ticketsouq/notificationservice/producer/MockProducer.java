package org.ticketsouq.notificationservice.producer;


import org.springframework.boot.CommandLineRunner;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.ticketsouq.notificationservice.event.*;

import java.util.List;
import java.util.UUID;

//@Component
//@Profile("local")
public class MockProducer implements CommandLineRunner {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public MockProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void run(String... args) {

//        sendPaymentSuccess();
//        sendPasswordReset();
//        sendEmailVerification();
//        sendPasswordChanged();
//        sendAccountGenerated();
        sendEventCancelled();

    }

    private void sendPaymentSuccess() {

        PaymentSuccessEvent event = new PaymentSuccessEvent(
            UUID.randomUUID(), // messageId
            UUID.fromString("11111111-1111-1111-1111-111111111111"), // userId
            UUID.fromString("11111111-1111-1111-1111-111111111111"), // eventId
            750L
        );

        kafkaTemplate.send(
            "notification.payment-success",
            event
        );

        System.out.println("PaymentSuccessEvent sent.");
    }

    private void sendPasswordReset() {

        PasswordResetEvent event = new PasswordResetEvent(
            UUID.randomUUID(),
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "123456789"
        );

        kafkaTemplate.send(
            "notification.password-reset",
            event
        );
    }

    private void sendEmailVerification() {

        EmailVerificationEvent event = new EmailVerificationEvent(
            UUID.randomUUID(),
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "omar.sseeddeekk@gmail.com",
            "123456789"
        );

        kafkaTemplate.send(
            "notification.email-verification",
            event
        );
    }

    private void sendPasswordChanged() {

        PasswordChangedEvent event = new PasswordChangedEvent(
            UUID.randomUUID(),
            UUID.fromString("11111111-1111-1111-1111-111111111111")
        );

        kafkaTemplate.send(
            "notification.password-changed",
            event
        );
    }

    private void sendAccountGenerated() {

        AccountGeneratedEvent event = new AccountGeneratedEvent(
            UUID.randomUUID(),
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            List.of(
                new AccountGeneratedEvent.AccountInfo(
                    UUID.randomUUID(),
                    "agent1@ticketaty.com",
                    "PASS123",
                    "AGENT"
                )
            )
        );

        kafkaTemplate.send(
            "notification.account-generated",
            event
        );
    }
    private void sendEventCancelled() {

        RefundCompletedEvent event = new RefundCompletedEvent(
            UUID.randomUUID(),
            UUID.fromString("11111111-1111-1111-1111-111111111111"), // userId
            UUID.fromString("11111111-1111-1111-1111-111111111111"), // eventId
            750L // refund amount
        );

        kafkaTemplate.send(
            "notification.refund-completed",
            event
        );

        System.out.println("EventCancelledEvent sent.");
    }

}
