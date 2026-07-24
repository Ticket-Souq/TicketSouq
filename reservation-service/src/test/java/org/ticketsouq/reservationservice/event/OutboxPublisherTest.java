package org.ticketsouq.reservationservice.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.ticketsouq.reservationservice.model.OutboxEvent;
import org.ticketsouq.reservationservice.model.enums.OutboxStatus;
import org.ticketsouq.reservationservice.repository.OutboxEventRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private OutboxPublisher outboxPublisher;

    @Test
    @DisplayName("Publish: saves outbox event with PENDING status and serialized payload")
    void publish_savesOutboxEvent() {
        String aggregateId = UUID.randomUUID().toString();
        String topic = "test-topic";
        TestEvent event = new TestEvent("test-value");

        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        outboxPublisher.publish(aggregateId, topic, event);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getAggregateId()).isEqualTo(aggregateId);
        assertThat(saved.getTopic()).isEqualTo(topic);
        assertThat(saved.getEventType()).isEqualTo(TestEvent.class.getName());
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getRetryCount()).isEqualTo(0);
        assertThat(saved.getPayload()).contains("test-value");
    }

    @Test
    @DisplayName("Publish duplicate event: skips gracefully on DataIntegrityViolation")
    void publish_duplicateEvent_skipsGracefully() {
        String aggregateId = UUID.randomUUID().toString();
        TestEvent event = new TestEvent("test-value");

        when(outboxEventRepository.save(any(OutboxEvent.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

        outboxPublisher.publish(aggregateId, "test-topic", event);

        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("Publish serialization failure: throws RuntimeException")
    void publish_serializationFailure_throws() {
        String aggregateId = UUID.randomUUID().toString();
        Object invalidEvent = new Object() {
            // ObjectMapper cannot serialize this anonymous object properly
        };

        assertThatThrownBy(() -> outboxPublisher.publish(aggregateId, "test-topic", invalidEvent))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to serialize outbox event");
    }

    private record TestEvent(String value) {}
}
