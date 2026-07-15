package org.ticketsouq.eventservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.eventservice.Client.UserServiceClient;
import org.ticketsouq.eventservice.dto.FrontendMap.*;
import org.ticketsouq.eventservice.model.Event;
import org.ticketsouq.eventservice.model.enums.EventStatus;
import org.ticketsouq.eventservice.repository.EventRepository;
import org.ticketsouq.eventservice.service.Search.SearchService;
import org.ticketsouq.sharedmodule.AuditService.events.AuditEvent;
import org.ticketsouq.sharedmodule.EventService.events.EventCancelledEvent;
import org.ticketsouq.sharedmodule.EventService.events.EventCreatedEvent;
import org.ticketsouq.sharedmodule.GeneralExceptions.ConflictException;
import org.ticketsouq.sharedmodule.GeneralExceptions.ResourceNotFoundException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final SearchService eventSearchService;
    private final ApplicationEventPublisher eventPublisher;
    private final EventFrontendMapper eventFrontendMapper;
    private final UserServiceClient userServiceClient;

    @Transactional
    public void create(UUID userId, CreateEventWithLayoutRequest request) {
        Event event = eventFrontendMapper.buildEvent(userId, request);
        eventRepository.save(event);
        eventSearchService.indexEvent(event);
        eventPublisher.publishEvent(new AuditEvent("Event Created", userId, "", Instant.now()));
        eventPublisher.publishEvent(toCreateMessage(event));
    }

    @Transactional(readOnly = true)
    public EventLayoutResponse getById(UUID id) {
        Event event = eventRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Event", id));
        return eventFrontendMapper.toEventLayoutResponse(event);
    }

    @Transactional(readOnly = true)
    public Page<EventCardResponse> getEvents(UUID userId, Pageable pageable) {
        String organization = userServiceClient.getOrganizationName(userId);

        return eventRepository.
            findFilteredEvents(organization, List.of(EventStatus.PUBLISHED, EventStatus.ACTIVE), pageable)
            .map(EventCardResponse::from);

    }

    @Transactional
    public void cancelEvent(UUID eventId, UUID userId) {

        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new ResourceNotFoundException("Event not found.", eventId));

        validateEventCanBeCancelled(event);

        event.setStatus(EventStatus.CANCELLED);

        eventSearchService.deleteFromIndex(event);
        eventRepository.save(event);

        eventPublisher.publishEvent(new AuditEvent("Event Canceled", userId, "", Instant.now()));
        eventPublisher.publishEvent(new EventCancelledEvent(UUID.randomUUID(), event.getId(), userId, Instant.now()));
    }

    private void validateEventCanBeCancelled(Event event) {
        if (event.getStatus() != EventStatus.PUBLISHED)
            throw new ConflictException("Only published events can be cancelled.");

        Instant deadline = event.getStartDate().minus(Duration.ofHours(24));

        if (Instant.now().isAfter(deadline))
            throw new ConflictException("Events cannot be cancelled less than 24 hours before start time.");

    }

    private EventCreatedEvent toCreateMessage(Event event) {
        return new EventCreatedEvent(
            event.getId(), event.getTitle(), event.getOrganization(), event.getCreatedBy(),
            event.getBookingModel().name(), event.getStartDate(), event.getFinishDate());
    }
}
