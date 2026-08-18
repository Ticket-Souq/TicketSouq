package org.ticketsouq.analyticsservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.analyticsservice.model.EventAnalytics;
import org.ticketsouq.analyticsservice.model.EventStatus;
import org.ticketsouq.analyticsservice.model.SalesRecord;
import org.ticketsouq.analyticsservice.repository.EventAnalyticsRepository;
import org.ticketsouq.analyticsservice.repository.SalesRecordRepository;
import org.ticketsouq.sharedmodule.EventService.events.EventActivatedEvent;
import org.ticketsouq.sharedmodule.EventService.events.EventCancelledEvent;
import org.ticketsouq.sharedmodule.EventService.events.EventCompletedEvent;
import org.ticketsouq.sharedmodule.EventService.events.EventCreatedEvent;
import org.ticketsouq.sharedmodule.ReservationService.events.ReservationCompletedEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsEventProcessingService {

    private final EventAnalyticsRepository eventAnalyticsRepository;
    private final SalesRecordRepository salesRecordRepository;

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
            .organizationName(event.organization())
            .createdBy(event.createdBy().toString())
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
    //  Reservation / payment event handlers
    // ──────────────────────────────────────────────

    @Transactional
    public void handleReservationCompleted(ReservationCompletedEvent event) {
        if (!event.success()) {
            log.info("Ignoring ReservationCompletedEvent with success=false for eventId={}", event.eventId());
            return;
        }
        String eventId = event.eventId().toString();
        int ticketsSold = event.tickets() == null ? 0 : event.tickets().size();
        eventAnalyticsRepository.findById(eventId).ifPresent(analytics -> {
            analytics.setTotalRevenue(analytics.getTotalRevenue().add(event.totalAmount()));
            analytics.setTotalTicketsSold(analytics.getTotalTicketsSold() + ticketsSold);
            eventAnalyticsRepository.save(analytics);
            upsertSalesRecord(eventId, analytics.getOrganizationName(), event.totalAmount(), ticketsSold);
        });
        log.info("Processed ReservationCompleted for event {}, amount={}, tickets={}", eventId, event.totalAmount(), ticketsSold);
    }

    // ──────────────────────────────────────────────
    //  Internal helpers
    // ──────────────────────────────────────────────

    private boolean isNewerThanLast(EventAnalytics analytics, Instant eventTimestamp) {
        if (analytics.getLastEventTimestamp() != null
            && !eventTimestamp.isAfter(analytics.getLastEventTimestamp())) {
            log.debug("Out-of-order or duplicate event for eventId={}, last={}, incoming={}",
                analytics.getEventId(), analytics.getLastEventTimestamp(), eventTimestamp);
            return false;
        }
        return true;
    }

    private void upsertSalesRecord(String eventId, String organizationName, BigDecimal amountDelta, int ticketsDelta) {
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        SalesRecord record = salesRecordRepository.findByEventIdAndSaleDate(eventId, today)
            .orElse(SalesRecord.builder()
                .eventId(eventId)
                .organizationName(organizationName)
                .saleDate(today)
                .ticketsSold(0)
                .revenue(BigDecimal.ZERO)
                .build());
        record.setRevenue(record.getRevenue().add(amountDelta));
        record.setTicketsSold(Math.max(0, record.getTicketsSold() + ticketsDelta));
        salesRecordRepository.save(record);
    }
}
