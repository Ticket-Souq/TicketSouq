package org.ticketsouq.apigateway.event;

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
public class KafkaCircuitBreakerWrapper {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @CircuitBreaker(name = "kafka", fallbackMethod = "fallback")
    public void send(String topic, String key, Object event) {
        try {
            kafkaTemplate.send(topic, key, event).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Kafka send interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Kafka send failed", e.getCause());
        } catch (TimeoutException e) {
            throw new RuntimeException("Kafka send timed out after 5s", e);
        }
    }

    private void fallback(String topic, String key, Object event, Throwable t) {
        log.error("Kafka unavailable — dropping event to topic {} (key={}, event={}): {}",
            topic, key, event.getClass().getSimpleName(), t.getMessage());
    }
}
