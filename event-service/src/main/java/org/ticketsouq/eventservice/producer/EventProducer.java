package org.ticketsouq.eventservice.producer;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES;
import org.ticketsouq.sharedmodule.EventService.events.EventCancelledEvent;

@Component
@RequiredArgsConstructor
public class EventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendEventCancelled(EventCancelledEvent event) {
        kafkaTemplate.send(TOPIC_NAMES.EVENT_CANCELLED, event);
    }
}
