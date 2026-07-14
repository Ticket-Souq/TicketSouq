package org.ticketsouq.eventservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.ticketsouq.eventservice.Mapper.EventMapper;
import org.ticketsouq.eventservice.dto.*;

import org.ticketsouq.eventservice.model.Event;
import org.ticketsouq.eventservice.model.enums.EventStatus;
import org.ticketsouq.eventservice.producer.EventProducer;
import org.ticketsouq.eventservice.repository.EventRepository;
import org.ticketsouq.sharedmodule.EventService.events.EventCancelledEvent;
import org.ticketsouq.sharedmodule.GeneralExceptions.ConflictException;
import org.ticketsouq.sharedmodule.GeneralExceptions.ForbiddenException;
import org.ticketsouq.sharedmodule.GeneralExceptions.ResourceNotFoundException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;
    private final AuthorizationService authorizationService;
    private final EventProducer eventProducer;

    @Transactional(readOnly = true)
    public Page<EventCardResponse> getPublicEvents(Pageable pageable) {

        return eventRepository
            .findByStatusOrderByStartDateAsc(EventStatus.PUBLISHED, pageable)
            .map(eventMapper::toCardResponse);
    }

    @Transactional
    public void cancelEvent(UUID eventId, UUID userId) {

        Event event = eventRepository.findById(eventId)
            .orElseThrow(() ->
                new ResourceNotFoundException("Event not found.", eventId));


        if(!authorizationService.validateCanManageEvent(event.getOrganizationId(), userId)){
                throw new ForbiddenException(
                    "User is not allowed to perform this action."
                );
        }

        validateEventCanBeCancelled(event);

        event.setStatus(EventStatus.CANCELLED);

        eventRepository.save(event);

        publishCancellationEvent(event);
    }

    private void validateEventCanBeCancelled(Event event) {

        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new ConflictException(
                "Only published events can be cancelled."
            );
        }

        Instant deadline = event.getStartDate().minus(Duration.ofHours(24));

        if (Instant.now().isAfter(deadline)) {
            throw new ConflictException(
                "Events cannot be cancelled less than 24 hours before start time."
            );
        }
    }

    private void publishCancellationEvent(Event event) {
        EventCancelledEvent cancelledEvent = new EventCancelledEvent(
            UUID.randomUUID(),
            event.getId(),
            event.getOrganizationId(),
            Instant.now()
        );
        eventProducer.sendEventCancelled(cancelledEvent);


    }
}
