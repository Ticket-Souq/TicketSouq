package org.ticketsouq.ticketservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.ticketservice.client.EventServiceClient;
import org.ticketsouq.ticketservice.dto.EventSnapshotResponse;
import org.ticketsouq.ticketservice.model.EventSnapshot;
import org.ticketsouq.ticketservice.repository.EventSnapshotRepository;
import org.ticketsouq.sharedmodule.EventService.events.EventActivatedEvent;
import org.ticketsouq.sharedmodule.EventService.events.EventCancelledEvent;
import org.ticketsouq.sharedmodule.EventService.events.EventCompletedEvent;
import org.ticketsouq.sharedmodule.EventService.events.EventCreatedEvent;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventSnapshotService {

    private final EventSnapshotRepository eventSnapshotRepository;
    private final EventServiceClient eventServiceClient;

    @Transactional
    public EventSnapshot resolve(UUID eventId) {
        EventSnapshot snapshot = eventSnapshotRepository.findById(eventId).orElse(null);
        if (snapshot != null && isHydrated(snapshot)) {
            return snapshot;
        }

        try {
            EventSnapshotResponse remote = eventServiceClient.getEvent(eventId);
            EventSnapshot merged = merge(snapshot, remote);
            return eventSnapshotRepository.save(merged);
        } catch (Exception ex) {
            if (snapshot != null) {
                log.warn("Using partial event snapshot for eventId={} after remote lookup failed: {}", eventId, ex.getMessage());
                return snapshot;
            }
            throw ex;
        }
    }

    @Transactional
    public void applyCreatedEvent(EventCreatedEvent event) {
        EventSnapshot snapshot = eventSnapshotRepository.findById(event.eventId())
            .orElseGet(() -> EventSnapshot.builder().eventId(event.eventId()).build());

        snapshot.setTitle(event.title());
        snapshot.setOrganization(event.organization());
        snapshot.setStartDate(event.startDateTime());
        snapshot.setFinishDate(event.endDateTime());
        if (snapshot.getStatus() == null) {
            snapshot.setStatus("PUBLISHED");
        }

        eventSnapshotRepository.save(snapshot);
    }

    @Transactional
    public void applyActivatedEvent(EventActivatedEvent event) {
        updateStatus(event.eventId(), "ACTIVE");
    }

    @Transactional
    public void applyCompletedEvent(EventCompletedEvent event) {
        updateStatus(event.eventId(), "COMPLETED");
    }

    @Transactional
    public void applyCancelledEvent(EventCancelledEvent event) {
        updateStatus(event.eventId(), "CANCELLED");
    }

    private void updateStatus(UUID eventId, String status) {
        EventSnapshot snapshot = eventSnapshotRepository.findById(eventId)
            .orElseGet(() -> EventSnapshot.builder().eventId(eventId).build());
        snapshot.setStatus(status);
        eventSnapshotRepository.save(snapshot);
    }

    private boolean isHydrated(EventSnapshot snapshot) {
        return snapshot.getPosterUrl() != null
            && snapshot.getTitle() != null
            && snapshot.getStartDate() != null
            && snapshot.getFinishDate() != null
            && snapshot.getStatus() != null;
    }

    private EventSnapshot merge(EventSnapshot existing, EventSnapshotResponse remote) {
        EventSnapshot snapshot = existing != null ? existing : EventSnapshot.builder().eventId(remote.id()).build();
        snapshot.setTitle(remote.title());
        snapshot.setDescription(remote.description());
        snapshot.setVenueTemplateId(remote.venueTemplateId());
        snapshot.setOrganization(remote.organization());
        snapshot.setStatus(remote.status());
        snapshot.setCategoryName(remote.categoryName());
        snapshot.setPosterUrl(remote.posterUrl());
        snapshot.setStartDate(remote.startDate());
        snapshot.setFinishDate(remote.finishDate());
        return snapshot;
    }
}
