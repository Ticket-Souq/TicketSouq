package org.ticketsouq.auditservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.AUDIT_EVENT;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic auditEventTopic() {
        return TopicBuilder.name(AUDIT_EVENT)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
