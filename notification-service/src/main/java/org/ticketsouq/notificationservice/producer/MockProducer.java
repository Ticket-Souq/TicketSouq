package org.ticketsouq.notificationservice.producer;


import org.springframework.boot.CommandLineRunner;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.ticketsouq.notificationservice.event.EmailVerificationEvent;

import java.util.UUID;

@Component
public class MockProducer implements CommandLineRunner {

    private final KafkaTemplate<String, EmailVerificationEvent> kafkaTemplate;

    public MockProducer(KafkaTemplate<String, EmailVerificationEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void run(String... args) {
        EmailVerificationEvent event = new EmailVerificationEvent(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "omar.sseeddeekk@gmail.com",
            "123456789"
        );
        kafkaTemplate.send("notification.email-verification", event);

        System.out.println("Mock EmailVerificationEvent sent.");
    }
}
