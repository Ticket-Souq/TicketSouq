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
import org.ticketsouq.eventservice.model.ZoneLock;
import org.ticketsouq.eventservice.model.enums.BookingModel;
import org.ticketsouq.eventservice.model.enums.EventStatus;
import org.ticketsouq.eventservice.repository.EventRepository;
import org.ticketsouq.eventservice.repository.SeatLockRepository;
import org.ticketsouq.eventservice.repository.SeatRepository;
import org.ticketsouq.eventservice.repository.ZoneLockRepository;
import org.ticketsouq.eventservice.service.Search.SearchService;
import org.ticketsouq.outbox.service.OutboxWriter;
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

import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.*;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
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
    private final ApplicationEventPublisher applicationObserver;
    private final OutboxWriter outboxWriter;
    private final EventMapper eventMapper;
    private final UserServiceClient userServiceClient;
    private final SeatLockRepository seatLockRepository;
    private final SeatRepository seatRepository;
    private final ZoneLockRepository zoneLockRepository;
    private final PosterStorageService posterStorageService;


    @Transactional
    public void create(UUID userId, CreateEventRequest request, MultipartFile poster, MultipartFile banner) {
        String posterUrl = posterStorageService.store(poster);
        String bannerUrl = posterStorageService.store(banner);
        Event event = eventMapper.buildEvent(userId, request, posterUrl, bannerUrl);
        eventRepository.save(event);
        SearchProvider.indexEvent(event);
        applicationObserver.publishEvent(toCreateMessage(event));
        outboxWriter.save(new AuditEvent("Event Created", userId, "", Instant.now()), AUDIT_EVENT, userId.toString());
        outboxWriter.save(toCreateMessage(event), EVENT_CREATED, event.getId().toString());

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
            outboxWriter.save(new OrganizerReservationCreatedEvent(
                event.getId(), userId, resolved), ORGANIZER_RESERVATION_CREATED, event.getId().toString());
        }
    }

    private record LockInfo(Set<UUID> lockedSeatIds, Map<UUID, Integer> adjustedRemaining) {}

    private LockInfo computeLockInfo(Event event) {
        Map<UUID, Integer> adjustedRemaining = new HashMap<>();
        Set<UUID> lockedSeatIds = Collections.emptySet();

        if (event.getSections() == null) {
            return new LockInfo(lockedSeatIds, adjustedRemaining);
        }

        if (event.getBookingModel() == BookingModel.SEAT) {
            List<UUID> sectionIds = event.getSections().stream()
                .map(Section::getId)
                .toList();
            List<Seat> seats = seatRepository.findBySectionIdIn(sectionIds);
            List<UUID> allSeatIds = seats.stream().map(Seat::getId).toList();

            if (!allSeatIds.isEmpty()) {
                List<SeatLock> activeLocks = seatLockRepository.findBySeatIdInAndExpiresAtAfter(allSeatIds, LocalDateTime.now());
                Set<UUID> foundLockedIds = activeLocks.stream()
                    .map(SeatLock::getSeatId)
                    .collect(Collectors.toSet());
                lockedSeatIds = foundLockedIds;

                Map<UUID, Long> lockedPerSection = seats.stream()
                    .filter(s -> foundLockedIds.contains(s.getId()))
                    .collect(Collectors.groupingBy(s -> s.getSection().getId(), Collectors.counting()));

                for (Section section : event.getSections()) {
                    long lockedCount = lockedPerSection.getOrDefault(section.getId(), 0L);
                    adjustedRemaining.put(section.getId(),
                        Math.max(0, section.getRemainingCapacity() - (int) lockedCount));
                }
            }
        } else if (event.getBookingModel() == BookingModel.ZONE) {
            LocalDateTime now = LocalDateTime.now();
            for (Section section : event.getSections()) {
                int activeSum = zoneLockRepository.sumActiveQuantityByZoneId(section.getId(), now);
                adjustedRemaining.put(section.getId(),
                    Math.max(0, section.getRemainingCapacity() - activeSum));
            }
        }

        return new LockInfo(lockedSeatIds, adjustedRemaining);
    }

    @Transactional(readOnly = true)
    public EventFullResponse getById(UUID id) {
        Event event = eventRepository.findEventById(id).orElseThrow(() -> new ResourceNotFoundException("Event", id));
        LockInfo info = computeLockInfo(event);
        return eventMapper.toEventFullResponse(event, info.lockedSeatIds, info.adjustedRemaining);
    }

    @Transactional(readOnly = true)
    public Page<EventCardResponse> getEvents(UUID userId, Pageable pageable) {
        String organization = userId != null ? userServiceClient.getOrganizationName(userId) : null;

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
                LockInfo info = computeLockInfo(event);
                return eventMapper.toEventFullResponse(event, info.lockedSeatIds, info.adjustedRemaining);
            });
    }

    @Transactional
    public void activateEvent(UUID eventId) {
        eventRepository.findById(eventId).ifPresent(event -> {
            if (event.getStatus() == EventStatus.PUBLISHED) {
                event.setStatus(EventStatus.ACTIVE);
                eventRepository.save(event);
                outboxWriter.save(new EventActivatedEvent(eventId, Instant.now()), EVENT_ACTIVATED, eventId.toString());
            }
        });
    }

    @Transactional
    public void completeEvent(UUID eventId) {
        eventRepository.findById(eventId).ifPresent(event -> {
            if (event.getStatus() == EventStatus.ACTIVE) {
                event.setStatus(EventStatus.COMPLETED);
                eventRepository.save(event);
                outboxWriter.save(new EventCompletedEvent(eventId, Instant.now()), EVENT_COMPLETED, eventId.toString());
                outboxWriter.save(new EventPayoutReleaseEvent(eventId,event.getOrganization(), Instant.now()), EVENT_PAYOUT_RELEASED, eventId.toString());
            }
        });
    }

    @Transactional
    public void completeEventDirectly(UUID eventId) {
        eventRepository.findById(eventId).ifPresent(event -> {
            if (event.getStatus() == EventStatus.PUBLISHED) {
                event.setStatus(EventStatus.COMPLETED);
                eventRepository.save(event);
                outboxWriter.save(new EventPayoutReleaseEvent(eventId,event.getOrganization(), Instant.now()), EVENT_PAYOUT_RELEASED, eventId.toString());
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

        outboxWriter.save(new AuditEvent("Event Canceled", userId, "", Instant.now()), AUDIT_EVENT, userId.toString());
        applicationObserver.publishEvent(new EventCancelledEvent(UUID.randomUUID(), event.getId(), Instant.now()));
        outboxWriter.save(new EventCancelledEvent(UUID.randomUUID(), event.getId(), Instant.now()), EVENT_CANCELLED, event.getId().toString());
        outboxWriter.save(new OrganizerReservationCancelledEvent(event.getId()), ORGANIZER_RESERVATION_CANCELLED, event.getId().toString());
    }

    private void validateEventCanBeCancelled(Event event) {
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new ConflictException("Only published events can be cancelled.");
        }

        Instant deadline = event.getStartDate().minus(Duration.ofHours(24));

        if (Instant.now().isAfter(deadline)) {
            throw new ConflictException("Events cannot be cancelled less than 24 hours before start time.");
        }

    }

    private EventCreatedEvent toCreateMessage(Event event) {
        return new EventCreatedEvent(
            event.getId(), event.getTitle(), event.getOrganization(), event.getCreatedBy(),
            event.getBookingModel().name(), event.getStartDate(), event.getFinishDate());
    }
}
