package org.ticketsouq.outbox.kafka;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaSender {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @CircuitBreaker(name = "outboxKafka", fallbackMethod = "kafkaUnavailable")
    public boolean send(String topic, String key, Object payload) {
        try {
            kafkaTemplate.send(topic, key, payload).get(10, TimeUnit.SECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OutboxKafkaException("Kafka send interrupted", e);
        } catch (ExecutionException e) {
            throw new OutboxKafkaException("Kafka send failed", e.getCause());
        } catch (TimeoutException e) {
            throw new OutboxKafkaException("Kafka send timed out", e);
        }
    }

    private boolean kafkaUnavailable(String topic, String key, Object payload, Throwable t) {
        log.warn("Kafka unavailable for topic={}, key={}: {}", topic, key, t.getMessage());
        return false;
    }
}
