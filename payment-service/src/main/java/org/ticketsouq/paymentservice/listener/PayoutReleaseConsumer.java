package org.ticketsouq.paymentservice.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.ticketsouq.paymentservice.service.PayoutService;
import org.ticketsouq.sharedmodule.EventService.events.EventPayoutReleaseEvent;

import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.EVENT_PAYOUT_RELEASED;

@Slf4j
@Component
@RequiredArgsConstructor
public class PayoutReleaseConsumer {

    private final PayoutService payoutService;

    @KafkaListener(topics = EVENT_PAYOUT_RELEASED, groupId = "${kafka.group-id:payment-service}")
    public void handlePayoutRelease(EventPayoutReleaseEvent event) {
        log.info("Received {} for eventId={}, organization={}", EVENT_PAYOUT_RELEASED, event.eventId(), event.organization());
        try {
            payoutService.handlePayoutRelease(event);
        } catch (Exception e) {
            log.error("Failed to handle payout for eventId={}: {}", event.eventId(), e.getMessage(), e);
            // do not rethrow to avoid infinite retry; payout can be retried via API
        }
    }
}
