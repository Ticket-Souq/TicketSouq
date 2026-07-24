package org.ticketsouq.reservationservice.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ticketsouq.reservationservice.model.OutboxEvent;
import org.ticketsouq.reservationservice.model.enums.OutboxStatus;
import org.ticketsouq.reservationservice.repository.OutboxEventRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutBoxUtilTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @InjectMocks
    private OutBoxUtil outBoxUtil;

    @Test
    @DisplayName("Publish success: marks event PUBLISHED and sets publishedAt")
    void handlePublishSuccess_setsPublishedAndTimestamp() {
        OutboxEvent event = OutboxEvent.builder()
            .id(UUID.randomUUID())
            .status(OutboxStatus.IN_PROGRESS)
            .retryCount(0)
            .build();

        when(outboxEventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        outBoxUtil.handlePublishSuccess(event);

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        verify(outboxEventRepository).save(event);
    }

    @Test
    @DisplayName("Publish failure below max retries: increments retryCount, sets PENDING")
    void handlePublishFailure_belowMaxRetries_setsPending() {
        OutboxEvent event = OutboxEvent.builder()
            .id(UUID.randomUUID())
            .status(OutboxStatus.IN_PROGRESS)
            .retryCount(2)
            .topic("test-topic")
            .build();

        when(outboxEventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        outBoxUtil.handlePublishFailure(event, new RuntimeException("test error"));

        assertThat(event.getRetryCount()).isEqualTo(3);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        verify(outboxEventRepository).save(event);
    }

    @Test
    @DisplayName("Publish failure at max retries: increments retryCount, sets FAILED")
    void handlePublishFailure_atMaxRetries_setsFailed() {
        OutboxEvent event = OutboxEvent.builder()
            .id(UUID.randomUUID())
            .status(OutboxStatus.IN_PROGRESS)
            .retryCount(4)
            .topic("test-topic")
            .build();

        when(outboxEventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        outBoxUtil.handlePublishFailure(event, new RuntimeException("test error"));

        assertThat(event.getRetryCount()).isEqualTo(5);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        verify(outboxEventRepository).save(event);
    }

    @Test
    @DisplayName("Publish failure event not found: silently ignored")
    void handlePublishFailure_eventNotFound_doesNothing() {
        OutboxEvent event = OutboxEvent.builder()
            .id(UUID.randomUUID())
            .build();

        when(outboxEventRepository.findById(event.getId())).thenReturn(Optional.empty());

        outBoxUtil.handlePublishFailure(event, new RuntimeException("test error"));

        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Reset stuck events: resets stale IN_PROGRESS events")
    void resetStuckEvents_resetsStaleInProgress() {
        int maxRetries = OutBoxUtil.MAX_RETRIES;
        Instant staleThreshold = Instant.now().minus(Duration.ofMinutes(OutBoxUtil.STALE_IN_PROGRESS_MINUTES + 1));

        when(outboxEventRepository.resetStuckInProgress(eq(maxRetries), any(Instant.class))).thenReturn(3);

        outBoxUtil.resetStuckEvents();

        verify(outboxEventRepository).resetStuckInProgress(eq(maxRetries), any(Instant.class));
    }

    @Test
    @DisplayName("Reset stuck events with no stuck events: logs nothing")
    void resetStuckEvents_noStuckEvents_logsNothing() {
        when(outboxEventRepository.resetStuckInProgress(anyInt(), any(Instant.class))).thenReturn(0);

        outBoxUtil.resetStuckEvents();

        verify(outboxEventRepository).resetStuckInProgress(anyInt(), any(Instant.class));
    }
}
