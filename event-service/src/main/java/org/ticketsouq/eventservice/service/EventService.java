package org.ticketsouq.eventservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.ticketsouq.eventservice.Client.UserServiceClient;
import org.ticketsouq.eventservice.dto.CreateEventRequest;
import org.ticketsouq.eventservice.dto.EventCardResponse;
import org.ticketsouq.eventservice.dto.EventFullResponse;
import org.ticketsouq.eventservice.mapper.EventMapper;
import org.ticketsouq.eventservice.model.Event;
import org.ticketsouq.eventservice.model.Seat;
import org.ticketsouq.eventservice.model.SeatLock;
import org.ticketsouq.eventservice.model.Section;
import org.ticketsouq.eventservice.model.enums.BookingModel;
import org.ticketsouq.eventservice.model.enums.EventStatus;
import org.ticketsouq.eventservice.repository.EventRepository;
import org.ticketsouq.eventservice.repository.SeatLockRepository;
import org.ticketsouq.eventservice.repository.SeatRepository;
import org.ticketsouq.eventservice.service.Search.SearchService;
import org.ticketsouq.sharedmodule.AuditService.events.AuditEvent;
import org.ticketsouq.sharedmodule.EventService.events.EventActivatedEvent;
import org.ticketsouq.sharedmodule.EventService.events.EventCancelledEvent;
import org.ticketsouq.sharedmodule.EventService.events.EventCompletedEvent;
import org.ticketsouq.sharedmodule.EventService.events.EventCreatedEvent;
import org.ticketsouq.sharedmodule.EventService.events.EventPayoutReleaseEvent;
import org.ticketsouq.sharedmodule.EventService.events.OrganizerReservationCancelledEvent;
import org.ticketsouq.sharedmodule.EventService.events.OrganizerReservationCreatedEvent;
import org.ticketsouq.sharedmodule.EventService.dto.TicketReservationDto;
import org.ticketsouq.sharedmodule.GeneralExceptions.ConflictException;
import org.ticketsouq.sharedmodule.GeneralExceptions.ResourceNotFoundException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final SearchService SearchProvider;
    private final ApplicationEventPublisher eventPublisher;
    private final EventMapper eventMapper;
    private final UserServiceClient userServiceClient;
    private final SeatLockRepository seatLockRepository;
    private final SeatRepository seatRepository;
    private final PosterStorageService posterStorageService;


    @Transactional
    public void create(UUID userId, CreateEventRequest request, MultipartFile poster) {
        String posterUrl = posterStorageService.store(poster);
        Event event = eventMapper.buildEvent(userId, request, posterUrl);
        eventRepository.save(event);
        SearchProvider.indexEvent(event);
        eventPublisher.publishEvent(new AuditEvent("Event Created", userId, "", Instant.now()));
        eventPublisher.publishEvent(toCreateMessage(event));

        if (request.reservations() != null && !request.reservations().isEmpty()) {
            List<UUID> unresolvedIds = request.reservations().stream()
                .filter(r -> r.holderName() == null || r.holderName().isBlank())
                .map(r -> userId)
                .distinct()
                .toList();

            Map<String, String> nameMap = unresolvedIds.isEmpty()
                ? Map.of()
                : userServiceClient.getUserNames(unresolvedIds);

            List<TicketReservationDto> resolved = request.reservations().stream()
                .map(r -> {
                    if (r.holderName() == null || r.holderName().isBlank()) {
                        String name = nameMap.getOrDefault(userId.toString(), "Unknown");
                        return new TicketReservationDto(r.price(), r.label(), r.sectionName(), name);
                    }
                    return r;
                })
                .toList();
            eventPublisher.publishEvent(new OrganizerReservationCreatedEvent(
                event.getId(), userId, resolved));
        }
    }

    @Transactional(readOnly = true)
    public EventFullResponse getById(UUID id) {
        Event event = eventRepository.findEventById(id).orElseThrow(() -> new ResourceNotFoundException("Event", id));

        if (event.getBookingModel() == BookingModel.SEAT && event.getSections() != null) {

            List<UUID> sectionIds = event.getSections().stream()
                .map(Section::getId)
                .toList();
            List<Seat> seats = seatRepository.findBySectionIdIn(sectionIds);
            List<UUID> allSeatIds = seats.stream().map(Seat::getId).toList();

            if (!allSeatIds.isEmpty()) {
                List<SeatLock> activeLocks = seatLockRepository.findBySeatIdInAndExpiresAtAfter(allSeatIds, LocalDateTime.now());
                Set<UUID> lockedSeatIds = activeLocks.stream()
                    .map(SeatLock::getSeatId)
                    .collect(Collectors.toSet());
                return eventMapper.toEventFullResponse(event, lockedSeatIds);
            }
        }

        return eventMapper.toEventFullResponse(event, Collections.emptySet());
    }

    @Transactional(readOnly = true)
    public Page<EventCardResponse> getEvents(UUID userId, Pageable pageable) {
        String organization = userServiceClient.getOrganizationName(userId);

        return eventRepository.
            findFilteredEvents(organization, List.of(EventStatus.PUBLISHED, EventStatus.ACTIVE), pageable)
            .map(EventCardResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<EventFullResponse> getManagementEvents(UUID userId, Pageable pageable) {
        String organization = userServiceClient.getOrganizationName(userId);
        return eventRepository.findByOrganizationWithSections(organization, pageable)
            .map(event -> {
                event.getSections().forEach(Section::getSeats);
                return eventMapper.toEventFullResponse(event, Collections.emptySet());
            });
    }

    @Transactional
    public void activateEvent(UUID eventId) {
        eventRepository.findById(eventId).ifPresent(event -> {
            if (event.getStatus() == EventStatus.PUBLISHED) {
                event.setStatus(EventStatus.ACTIVE);
                eventRepository.save(event);
                eventPublisher.publishEvent(new EventActivatedEvent(eventId, Instant.now()));
            }
        });
    }

    @Transactional
    public void completeEvent(UUID eventId) {
        eventRepository.findById(eventId).ifPresent(event -> {
            if (event.getStatus() == EventStatus.ACTIVE) {
                event.setStatus(EventStatus.COMPLETED);
                eventRepository.save(event);
                eventPublisher.publishEvent(new EventCompletedEvent(eventId, Instant.now()));
                eventPublisher.publishEvent(new EventPayoutReleaseEvent(eventId,event.getOrganization(), Instant.now()));
            }
        });
    }

    @Transactional
    public void completeEventDirectly(UUID eventId) {
        eventRepository.findById(eventId).ifPresent(event -> {
            if (event.getStatus() == EventStatus.PUBLISHED) {
                event.setStatus(EventStatus.COMPLETED);
                eventRepository.save(event);
                eventPublisher.publishEvent(new EventPayoutReleaseEvent(eventId,event.getOrganization(), Instant.now()));
            }
        });
    }

    @Transactional
    public void cancelEvent(UUID eventId, UUID userId) {

        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new ResourceNotFoundException("Event not found.", eventId));

        validateEventCanBeCancelled(event);

        event.setStatus(EventStatus.CANCELLED);

        SearchProvider.deleteFromIndex(event);
        eventRepository.save(event);

        eventPublisher.publishEvent(new AuditEvent("Event Canceled", userId, "", Instant.now()));
        eventPublisher.publishEvent(new EventCancelledEvent(UUID.randomUUID(), event.getId(), Instant.now()));
        eventPublisher.publishEvent(new OrganizerReservationCancelledEvent(event.getId()));
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
