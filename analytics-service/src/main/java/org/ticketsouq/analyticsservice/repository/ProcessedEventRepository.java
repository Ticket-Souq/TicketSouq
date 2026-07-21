package org.ticketsouq.analyticsservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ticketsouq.analyticsservice.model.ProcessedEvent;

import java.util.Optional;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {

    Optional<ProcessedEvent> findByTopicAndMessageId(String topic, String messageId);

    boolean existsByTopicAndMessageId(String topic, String messageId);
}
