package org.ticketsouq.eventservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.eventservice.dto.CreateEventRequest;
import org.ticketsouq.eventservice.dto.EventRequest;
import org.ticketsouq.eventservice.dto.EventResponse;
import org.ticketsouq.eventservice.dto.UpdateEventRequest;
import org.ticketsouq.eventservice.model.Event;
import org.ticketsouq.eventservice.model.Seat;
import org.ticketsouq.eventservice.model.Section;
import org.ticketsouq.eventservice.model.enums.BookingModel;
import org.ticketsouq.eventservice.model.enums.EventStatus;
import org.ticketsouq.eventservice.model.enums.SeatStatus;
import org.ticketsouq.eventservice.repository.EventRepository;
import org.ticketsouq.eventservice.repository.SeatRepository;
import org.ticketsouq.eventservice.repository.SectionRepository;
import org.ticketsouq.sharedmodule.EventService.events.EventCancelledEvent;
import org.ticketsouq.sharedmodule.EventService.events.EventCreatedEvent;
import org.ticketsouq.sharedmodule.EventService.events.EventStatusChangedEvent;
import org.ticketsouq.sharedmodule.EventService.events.EventUpdatedEvent;
import org.ticketsouq.sharedmodule.GeneralExceptions.ResourceNotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final SectionRepository sectionRepository;
    private final SeatRepository seatRepository;
    private final EventSearchService eventSearchService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public EventResponse create(CreateEventRequest request) {
        Event event = Event.builder()
                .title(request.title())
                .description(request.description())
                .venueId(request.venueId())
                .organizationId(request.organizationId())
                .createdBy(request.createdBy())
                .PosterUrl(request.posterUrl())
                .status(request.status())
                .bookingModel(request.bookingModel())
                .startDate(request.startDate())
                .finishDate(request.finishDate())
                .build();

        event = eventRepository.save(event);

        if (request.sections() != null) {
            for (CreateEventRequest.SectionWithSeats sectionReq : request.sections()) {
                Section section = Section.builder()
                        .event(event)
                        .name(sectionReq.name())
                        .capacity(sectionReq.capacity())
                        .remainingCapacity(sectionReq.capacity())
                        .color(sectionReq.color())
                        .price(sectionReq.price())
                        .build();
                section = sectionRepository.save(section);

                if (request.bookingModel() == BookingModel.SEAT && sectionReq.seats() != null) {
                    for (CreateEventRequest.SeatInSection seatReq : sectionReq.seats()) {
                        Seat seat = Seat.builder()
                                .section(section)
                                .row(seatReq.row())
                                .col(seatReq.col())
                                .lable(seatReq.lable())
                                .status(SeatStatus.AVAILABLE)
                                .price(seatReq.price())
                                .build();
                        seatRepository.save(seat);
                    }
                }
            }
        }

        eventSearchService.indexEvent(event);

        eventPublisher.publishEvent(new EventCreatedEvent(
                event.getId(), event.getTitle(), event.getOrganizationId(),
                event.getStatus().name(), event.getBookingModel().name(),
                event.getStartDate(), event.getFinishDate()));

        return EventResponse.from(event);
    }

    @Transactional(readOnly = true)
    public EventResponse getById(UUID id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event", id));
        return EventResponse.from(event);
    }

    @Transactional(readOnly = true)
    public Page<EventResponse> getPublicEvents(Pageable pageable) {
        return eventRepository.findByStatusIn(List.of(EventStatus.PUBLISHED, EventStatus.ACTIVE), pageable)
                .map(EventResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<EventResponse> getOrganizationEvents(UUID organizationId, EventStatus status, Pageable pageable) {
        if (status != null) {
            return eventRepository.findByOrganizationIdAndStatus(organizationId, status, pageable)
                    .map(EventResponse::from);
        }
        return eventRepository.findByOrganizationId(organizationId, pageable)
                .map(EventResponse::from);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> getAll() {
        return eventRepository.findAll().stream()
                .map(EventResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<EventResponse> getByStatus(EventStatus status, Pageable pageable) {
        return eventRepository.findByStatus(status, pageable)
                .map(EventResponse::from);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> getByOrganizationId(UUID organizationId, Pageable pageable) {
        return eventRepository.findByOrganizationId(organizationId, pageable).stream()
                .map(EventResponse::from)
                .toList();
    }

    @Transactional
    public EventResponse update(UUID id, UpdateEventRequest request) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event", id));

        event.setTitle(request.title());
        event.setDescription(request.description());
        event.setVenueId(request.venueId());
        event.setOrganizationId(request.organizationId());
        event.setCreatedBy(request.createdBy());
        event.setPosterUrl(request.posterUrl());
        event.setStatus(request.status());
        event.setBookingModel(request.bookingModel());
        event.setStartDate(request.startDate());
        event.setFinishDate(request.finishDate());

        event = eventRepository.save(event);

        eventSearchService.indexEvent(event);

        eventPublisher.publishEvent(new EventUpdatedEvent(
                event.getId(), event.getTitle(), event.getOrganizationId(),
                event.getStatus().name(), event.getBookingModel().name(),
                event.getStartDate(), event.getFinishDate()));

        return EventResponse.from(event);
    }

    @Transactional
    public void cancel(UUID id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event", id));
        eventSearchService.deleteFromIndex(event);
        eventRepository.delete(event);
    }

    @Transactional
    public EventResponse updateStatus(UUID id, EventStatus newStatus) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event", id));
        EventStatus oldStatus = event.getStatus();
        event.setStatus(newStatus);
        event = eventRepository.save(event);

        eventPublisher.publishEvent(new EventStatusChangedEvent(
                event.getId(), oldStatus.name(), newStatus.name()));

        if (newStatus == EventStatus.CANCELLED) {
            eventPublisher.publishEvent(new EventCancelledEvent(
                    event.getId(), event.getOrganizationId()));
        }

        return EventResponse.from(event);
    }

    @Transactional
    public int activateScheduledEvents() {
        List<Event> events = eventRepository
                .findByStartDateBeforeAndStatus(Instant.now(), EventStatus.PUBLISHED);
        for (Event event : events) {
            EventStatus oldStatus = event.getStatus();
            event.setStatus(EventStatus.ACTIVE);
            eventRepository.save(event);
            eventPublisher.publishEvent(new EventStatusChangedEvent(
                    event.getId(), oldStatus.name(), EventStatus.ACTIVE.name()));
        }
        log.info("Activated {} events", events.size());
        return events.size();
    }

    @Transactional
    public int completeExpiredEvents() {
        List<Event> events = eventRepository
                .findByFinishDateBeforeAndStatus(Instant.now(), EventStatus.ACTIVE);
        for (Event event : events) {
            EventStatus oldStatus = event.getStatus();
            event.setStatus(EventStatus.COMPLETED);
            eventRepository.save(event);
            eventPublisher.publishEvent(new EventStatusChangedEvent(
                    event.getId(), oldStatus.name(), EventStatus.COMPLETED.name()));
        }
        log.info("Completed {} events", events.size());
        return events.size();
    }
}
