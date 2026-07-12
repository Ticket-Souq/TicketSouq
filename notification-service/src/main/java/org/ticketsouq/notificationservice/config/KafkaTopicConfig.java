package org.ticketsouq.notificationservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic emailVerificationTopic() {
        return TopicBuilder.name("notification.email-verification")
            .partitions(6)
            .replicas(1)
            .build();
    }
    @Bean
    public NewTopic passwordResetTopic() {
        return TopicBuilder.name("notification.password-reset")
            .partitions(6)
            .replicas(1)
            .build();
    }
    @Bean
    public NewTopic passwordChangedTopic() {
        return TopicBuilder.name("notification.password-changed")
            .partitions(6)
            .replicas(1)
            .build();
    }
    @Bean
    public NewTopic refundCompletedTopic() {
        return TopicBuilder.name("notification.refund-completed")
            .partitions(6)
            .replicas(1)
            .build();
    }
    @Bean
    public NewTopic AccountGeneratedTopic() {
        return TopicBuilder.name("notification.account-generated")
            .partitions(6)
            .replicas(1)
            .build();
    }
    @Bean
    public NewTopic PaymentSuccessTopic() {
        return TopicBuilder.name("notification.payment-success")
            .partitions(6)
            .replicas(1)
            .build();
    }

}
