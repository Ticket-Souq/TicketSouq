package org.ticketsouq.reservationservice.recovery;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.ticketsouq.reservationservice.core.SagaOrchestrator;
import org.ticketsouq.reservationservice.model.SagaInstance;
import org.ticketsouq.reservationservice.model.enums.SagaStatus;
import org.ticketsouq.reservationservice.repository.SagaInstanceRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class SagaRecoveryJob {

    private static final Duration STEP_TIMEOUT = Duration.ofMinutes(10);
    private static final int BATCH_SIZE = 100;
    private final SagaInstanceRepository sagaInstanceRepository;
    private final SagaOrchestrator sagaOrchestrator;
    private final AtomicBoolean running = new AtomicBoolean();


    @PostConstruct
    public void recoverOnStartup() {
        recoverStuckSagas("startup");
    }

    @Scheduled(fixedDelay = 30000)
    public void recoverPeriodic() {
        recoverStuckSagas("periodic");
    }

    @Scheduled(fixedDelay = 60000)
    public void checkSagaTimeouts() {
        Instant threshold = Instant.now().minus(STEP_TIMEOUT);
        List<SagaInstance> timedOutSagas = sagaInstanceRepository.findBySagaStatusAndLastStepCompletedAtBefore(
            SagaStatus.ACTIVE, threshold);

        if (timedOutSagas.isEmpty()) return;

        log.warn("Found {} saga(s) timed out (no progress for {} minutes), starting compensation", timedOutSagas.size(), STEP_TIMEOUT.toMinutes());

        for (SagaInstance saga : timedOutSagas) {
            try {
                log.warn("Saga {} timed out at step {}, starting compensation", saga.getId(), saga.getCurrentStep());
                sagaOrchestrator.startCompensation(saga, "Saga timeout: no progress for " + STEP_TIMEOUT.toMinutes() + " minutes at step " + saga.getCurrentStep());
            } catch (Exception e) {
                log.error("Failed to start compensation for timed-out saga {}: {}", saga.getId(), e.getMessage(), e);
            }
        }
    }

    private void recoverStuckSagas(String trigger) {

        if (!running.compareAndSet(false, true)) return;

        try {
            while (true) {
                Page<SagaInstance> batch = sagaInstanceRepository.findBySagaStatusIn(List.of(SagaStatus.ACTIVE, SagaStatus.COMPENSATING), PageRequest.of(0, BATCH_SIZE));
                if (batch.isEmpty()) break;

                for (SagaInstance saga : batch.getContent()) {
                    try {
                        sagaOrchestrator.recoverSaga(saga.getReservationId());
                    } catch (Exception e) {
                        log.error("[{}] Failed to recover saga {}: {}", trigger, saga.getId(), e.getMessage(), e);
                    }
                }

                if (!batch.hasNext()) break;
            }
        } finally {
            running.set(false);
        }
    }
}
