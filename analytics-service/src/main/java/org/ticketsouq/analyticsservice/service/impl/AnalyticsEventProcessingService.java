package org.ticketsouq.analyticsservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.analyticsservice.model.EventAnalytics;
import org.ticketsouq.analyticsservice.model.EventStatus;
import org.ticketsouq.analyticsservice.model.ProcessedEvent;
import org.ticketsouq.analyticsservice.model.SalesRecord;
import org.ticketsouq.analyticsservice.repository.EventAnalyticsRepository;
import org.ticketsouq.analyticsservice.repository.ProcessedEventRepository;
import org.ticketsouq.analyticsservice.repository.SalesRecordRepository;
import org.ticketsouq.sharedmodule.EventService.events.*;
import org.ticketsouq.sharedmodule.PaymentService.events.PaymentFailedEvent;
import org.ticketsouq.sharedmodule.PaymentService.events.PaymentSuccessEvent;
import org.ticketsouq.sharedmodule.PaymentService.events.RefundCompletedEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsEventProcessingService {

    private final EventAnalyticsRepository eventAnalyticsRepository;
    private final SalesRecordRepository salesRecordRepository;
    private final ProcessedEventRepository processedEventRepository;

    // ──────────────────────────────────────────────
    //  Event lifecycle handlers
    // ──────────────────────────────────────────────

    @Transactional
    public void handleEventCreated(EventCreatedEvent event) {
        String eventId = event.eventId().toString();
        if (eventAnalyticsRepository.findById(eventId).isPresent()) {
            log.info("Duplicate EventCreatedEvent for eventId={}, skipping", eventId);
            return;
        }
        eventAnalyticsRepository.save(EventAnalytics.builder()
            .eventId(eventId)
            .organizationId(event.organization())
            .title(event.title())
            .status(EventStatus.CREATED)
            .totalRevenue(BigDecimal.ZERO)
            .totalTicketsSold(0)
            .startDateTime(event.startDateTime())
            .endDateTime(event.endDateTime())
            .lastEventTimestamp(event.startDateTime())
            .build());
        log.info("Created analytics record for event {} ({})", eventId, event.title());
    }

    @Transactional
    public void handleEventActivated(EventActivatedEvent event) {
        String eventId = event.eventId().toString();
        EventAnalytics analytics = eventAnalyticsRepository.findById(eventId).orElse(null);
        if (analytics == null) {
            log.warn("EventActivatedEvent for unknown eventId={}, ignoring", eventId);
            return;
        }
        if (!isNewerThanLast(analytics, event.activatedAt())) return;
        analytics.setStatus(EventStatus.ACTIVATED);
        analytics.setLastEventTimestamp(event.activatedAt());
        eventAnalyticsRepository.save(analytics);
        log.info("Activated analytics for event {}", eventId);
    }

    @Transactional
    public void handleEventCompleted(EventCompletedEvent event) {
        String eventId = event.eventId().toString();
        EventAnalytics analytics = eventAnalyticsRepository.findById(eventId).orElse(null);
        if (analytics == null) {
            log.warn("EventCompletedEvent for unknown eventId={}, ignoring", eventId);
            return;
        }
        if (!isNewerThanLast(analytics, event.completedAt())) return;
        analytics.setStatus(EventStatus.COMPLETED);
        analytics.setLastEventTimestamp(event.completedAt());
        eventAnalyticsRepository.save(analytics);
        log.info("Completed analytics for event {}", eventId);
    }

    @Transactional
    public void handleEventCancelled(EventCancelledEvent event) {
        if (!tryClaimEvent(EVENT_CANCELLED, event.messageId().toString())) return;

        String eventId = event.eventId().toString();
        EventAnalytics analytics = eventAnalyticsRepository.findById(eventId).orElse(null);
        if (analytics == null) {
            log.warn("EventCancelledEvent for unknown eventId={}, ignoring", eventId);
            return;
        }
        if (!isNewerThanLast(analytics, event.cancelledAt())) return;
        analytics.setStatus(EventStatus.CANCELLED);
        analytics.setLastEventTimestamp(event.cancelledAt());
        eventAnalyticsRepository.save(analytics);
        log.info("Cancelled analytics for event {}", eventId);
    }

    // ──────────────────────────────────────────────
    //  Payment event handlers
    // ──────────────────────────────────────────────

    @Transactional
    public void handlePaymentSuccess(PaymentSuccessEvent event) {
        if (!tryClaimEvent(PAYMENT_SUCCESS, event.messageId().toString())) return;

        String eventId = event.eventId().toString();
        eventAnalyticsRepository.findById(eventId).ifPresent(analytics -> {
            analytics.setTotalRevenue(analytics.getTotalRevenue().add(event.amount()));
            eventAnalyticsRepository.save(analytics);
        });
        upsertSalesRecord(eventId, event.amount());
        log.info("Processed PaymentSuccess for event {}, amount={}", eventId, event.amount());
    }

    @Transactional
    public void handlePaymentFailed(PaymentFailedEvent event) {
        if (!tryClaimEvent(PAYMENT_FAILED, event.messageId().toString())) return;

        String eventId = event.eventId().toString();
        log.info("Recorded PaymentFailed for event {}, amount={}", eventId, event.amount());
    }

    @Transactional
    public void handleRefundCompleted(RefundCompletedEvent event) {
        if (!tryClaimEvent(PAYMENT_REFUNDED, event.messageId().toString())) return;

        String eventId = event.eventId().toString();
        eventAnalyticsRepository.findById(eventId).ifPresent(analytics -> {
            analytics.setTotalRevenue(analytics.getTotalRevenue().subtract(event.amount()));
            eventAnalyticsRepository.save(analytics);
        });
        upsertSalesRecord(eventId, event.amount().negate());
        log.info("Processed RefundCompleted for event {}, amount={}", eventId, event.amount());
    }

    // ──────────────────────────────────────────────
    //  Internal helpers
    // ──────────────────────────────────────────────

    private boolean tryClaimEvent(String topic, String messageId) {
        try {
            processedEventRepository.saveAndFlush(ProcessedEvent.builder()
                .topic(topic)
                .messageId(messageId)
                .processedAt(Instant.now())
                .build());
            return true;
        } catch (DataIntegrityViolationException e) {
            log.debug("Duplicate event {}/{} already processed", topic, messageId);
            return false;
        }
    }

    private boolean isNewerThanLast(EventAnalytics analytics, Instant eventTimestamp) {
        if (analytics.getLastEventTimestamp() != null
            && !eventTimestamp.isAfter(analytics.getLastEventTimestamp())) {
            log.debug("Out-of-order or duplicate event for eventId={}, last={}, incoming={}",
                analytics.getEventId(), analytics.getLastEventTimestamp(), eventTimestamp);
            return false;
        }
        return true;
    }

    private void upsertSalesRecord(String eventId, BigDecimal amountDelta) {
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        SalesRecord record = salesRecordRepository.findByEventIdAndSaleDate(eventId, today)
            .orElse(SalesRecord.builder()
                .eventId(eventId)
                .saleDate(today)
                .ticketsSold(0)
                .revenue(BigDecimal.ZERO)
                .build());
        record.setRevenue(record.getRevenue().add(amountDelta));
        salesRecordRepository.save(record);
    }
}
