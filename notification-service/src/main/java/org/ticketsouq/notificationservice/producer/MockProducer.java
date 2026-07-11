package org.ticketsouq.notificationservice.producer;


import org.springframework.boot.CommandLineRunner;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.ticketsouq.notificationservice.event.*;

import java.util.List;
import java.util.UUID;

@Component
public class MockProducer implements CommandLineRunner {
    private final KafkaTemplate<String, AccountGeneratedEvent> kafkaTemplate;

    public MockProducer(KafkaTemplate<String, AccountGeneratedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }
//    private final KafkaTemplate<String, PaymentSuccessEvent> kafkaTemplate;
//
//    public MockProducer(KafkaTemplate<String, PaymentSuccessEvent> kafkaTemplate) {
//        this.kafkaTemplate = kafkaTemplate;
//    }
    //    private final KafkaTemplate<String, EmailVerificationEvent> kafkaTemplate;
//public MockProducer(KafkaTemplate<String, EmailVerificationEvent> kafkaTemplate) {
//        this.kafkaTemplate = kafkaTemplate;
//    }
//    private final KafkaTemplate<String, PasswordResetEvent> kafkaTemplate;
//
//    public MockProducer(KafkaTemplate<String, PasswordResetEvent> kafkaTemplate) {
//        this.kafkaTemplate = kafkaTemplate;
//    }
//

//    private final KafkaTemplate<String, PasswordChangedEvent> kafkaTemplate;
//
//    public MockProducer(KafkaTemplate<String, PasswordChangedEvent> kafkaTemplate) {
//        this.kafkaTemplate = kafkaTemplate;
//    }


    @Override
    public void run(String... args) {
        AccountGeneratedEvent event = new AccountGeneratedEvent(
            UUID.fromString("22222222-2222-2222-2222-222222222222"), // Organization Head User ID
            List.of(
                new AccountGeneratedEvent.AccountInfo(
                    UUID.fromString("22222222-2222-2222-2222-222222222222"),
                    "agent325@ticketaty.com",
                    "AGENT@123",
                    "AGENT"
                ),
                new AccountGeneratedEvent.AccountInfo(
                    UUID.fromString("33333333-3333-3333-3333-333333333333"),
                    "agent587@ticketaty.com",
                    "AGENT@571",
                    "AGENT"
                ),
                new AccountGeneratedEvent.AccountInfo(
                    UUID.fromString("44444444-4444-4444-4444-444444444444"),
                    "consumer754@ticketaty.com",
                    "CONSUMER@909",
                    "CONSUMER"
                )
            )

        );
//
        kafkaTemplate.send("notification.account-generated", event);
//
//        System.out.println("Mock AccountGeneratedEvent sent.");

//        PaymentSuccessEvent event = new PaymentSuccessEvent(
//            UUID.fromString("11111111-1111-1111-1111-111111111111"),
//            UUID.fromString("22222222-2222-2222-2222-222222222223"),
//            750L
//        );
//
//        kafkaTemplate.send(
//            "notification.payment-success",
//            event
//        );
//
//        System.out.println("Mock PaymentSuccessEvent sent1.");
//        kafkaTemplate.send(
//            "notification.payment-success",
//            event
//        );
//
//        System.out.println("Mock PaymentSuccessEvent sent2.");
//        kafkaTemplate.send(
//            "notification.payment-success",
//            event
//        );
//
//        System.out.println("Mock PaymentSuccessEvent sent3.");
//    }
//        PasswordChangedEvent event = new PasswordChangedEvent(
//            UUID.fromString("11111111-1111-1111-1111-111111111111")
//        );
//        kafkaTemplate.send(
//            "notification.password-changed",
//            event
//        );


//        System.out.println("start 1");
//
//        PasswordResetEvent event = new PasswordResetEvent(
//            UUID.fromString("11111111-1111-1111-1111-111111111111"),
//            "123456789"
//        );
//        System.out.println("start 2");
//
//        kafkaTemplate.send(
//            "notification.password-reset",
//            event
//        );
//        System.out.println("start 3");
//        //EmailVerificationEvent
//        EmailVerificationEvent event = new EmailVerificationEvent(
//            UUID.fromString("11111111-1111-1111-1111-111111111111"),
//            "omar.sseeddeekk@gmail.com",
//            "123456789"
//        );
//        kafkaTemplate.send("notification.email-verification", event);
//
//        System.out.println("Mock EmailVerificationEvent sent.");


    }
}

