package org.ticketsouq.notificationservice.producer;


import org.springframework.boot.CommandLineRunner;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.ticketsouq.notificationservice.event.EmailVerificationEvent;
import org.ticketsouq.notificationservice.event.PasswordChangedEvent;
import org.ticketsouq.notificationservice.event.PasswordResetEvent;

import java.util.UUID;

//@Component
public class MockProducer implements CommandLineRunner {

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

    private final KafkaTemplate<String, PasswordChangedEvent> kafkaTemplate;

    public MockProducer(KafkaTemplate<String, PasswordChangedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }


    @Override
    public void run(String... args) {
        PasswordChangedEvent event = new PasswordChangedEvent(
            UUID.fromString("11111111-1111-1111-1111-111111111111")
        );
        kafkaTemplate.send(
            "notification.password-changed",
            event
        );


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
