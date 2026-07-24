package org.ticketsouq.reservationservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.*;

@Configuration
public class KafkaTopicConfig {

    private static final int PARTITIONS = 3;
    private static final short REPLICAS = 1;

    private NewTopic createTopic(String name) {
        return TopicBuilder.name(name)
            .partitions(PARTITIONS)
            .replicas(REPLICAS)
            .build();
    }

    @Bean
    public NewTopic reservationCreatedTopic() {
        return createTopic(PAYMENT_REFUND_REQUEST);
    }


    @Bean
    public NewTopic sagaLockConfirmCommandTopic() {
        return createTopic(SAGA_LOCK_CONFIRM_COMMAND);
    }

    @Bean
    public NewTopic sagaLockConfirmCompensateTopic() {
        return createTopic(SAGA_LOCK_CONFIRM_COMPENSATE);
    }

}
