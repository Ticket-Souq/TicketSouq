package org.ticketsouq.reservationservice.config;

import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.kafka.autoconfigure.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@Slf4j
public class KafkaConfig {


    @Bean
    ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
        ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
        ConsumerFactory<Object, Object> consumerFactory,
        DefaultErrorHandler errorHandler) {

        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();

        configurer.configure(factory, consumerFactory);
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }

    @Bean
    public DefaultErrorHandler sagaReplyErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        FixedBackOff backOff = new FixedBackOff(2000L, 3);
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer,backOff);
        handler.addRetryableExceptions(Exception.class);
        handler.addNotRetryableExceptions(IllegalArgumentException.class, IllegalStateException.class);
        handler.setRetryListeners((record, ex, deliveryAttempt) -> {
            log.warn("Retrying saga reply for topic={}, partition={}, offset={}, attempt={}",
                record.topic(), record.partition(), record.offset(), deliveryAttempt);
        });
        return handler;
    }
}
