package org.ticketsouq.notificationservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.*;

@Configuration
public class KafkaTopicConfig {

    private static final int PARTITIONS = 6;
    private static final short REPLICAS = 1;

    private NewTopic createTopic(String name) {
        return TopicBuilder.name(name)
            .partitions(PARTITIONS)
            .replicas(REPLICAS)
            .build();
    }

    @Bean
    public NewTopic emailVerificationTopic() {
        return createTopic(USER_EMAIL_VERIFICATION);
    }

    @Bean
    public NewTopic passwordResetTopic() {
        return createTopic(USER_PASSWORD_RESET);
    }

    @Bean
    public NewTopic passwordChangedTopic() {
        return createTopic(USER_PASSWORD_CHANGE);
    }

    @Bean
    public NewTopic refundCompletedTopic() {
        return createTopic(PAYMENT_REFUNDED);
    }

    @Bean
    public NewTopic accountGeneratedTopic() {
        return createTopic(ACCOUNTS_GENERATED);
    }

    @Bean
    public NewTopic paymentSuccessTopic() {
        return createTopic(PAYMENT_SUCCESS);
    }

}
