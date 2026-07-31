package org.ticketsouq.analyticsservice.config.kafka;

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
    public NewTopic eventCreatedTopic() {
        return createTopic(EVENT_CREATED);
    }

    @Bean
    public NewTopic eventActivatedTopic() {
        return createTopic(EVENT_ACTIVATED);
    }

    @Bean
    public NewTopic eventCompletedTopic() {
        return createTopic(EVENT_COMPLETED);
    }

    @Bean
    public NewTopic eventCancelledTopic() {
        return createTopic(EVENT_CANCELLED);
    }

    @Bean
    public NewTopic paymentSuccessTopic() {
        return createTopic(PAYMENT_SUCCESS);
    }

    @Bean
    public NewTopic paymentFailedTopic() {
        return createTopic(PAYMENT_FAILED);
    }

    @Bean
    public NewTopic refundCompletedTopic() {
        return createTopic(PAYMENT_REFUNDED);
    }
}
