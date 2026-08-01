package org.ticketsouq.eventservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.ticketsouq.eventservice.Client.UserServiceClient;
import org.ticketsouq.eventservice.dto.CreateEventRequest;
import org.ticketsouq.eventservice.dto.EventCardResponse;
import org.ticketsouq.eventservice.dto.EventFullResponse;
import org.ticketsouq.eventservice.mapper.EventMapper;
import org.ticketsouq.eventservice.model.Event;
import org.ticketsouq.eventservice.model.Seat;
import org.ticketsouq.eventservice.model.Section;
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
import org.ticketsouq.sharedmodule.GeneralExceptions.ConflictException;
import org.ticketsouq.sharedmodule.GeneralExceptions.ResourceNotFoundException;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock private EventRepository eventRepository;
    @Mock private SearchService eventSearchService;
    @Mock private EventMapper eventMapper;
    @Mock private UserServiceClient userServiceClient;
    @Mock private SeatLockRepository seatLockRepository;
    @Mock private SeatRepository seatRepository;
    @Mock private PosterStorageService posterStorageService;
    @Mock private  OutboxWriter outboxWriter;
    @Mock private ZoneLockRepository zoneLockRepository;


    private EventService eventService;

    @BeforeEach
    void setUp() {
        eventService = new EventService(eventRepository, eventSearchService, outboxWriter,
            eventMapper, userServiceClient, seatLockRepository, seatRepository, zoneLockRepository,posterStorageService);
    }

    @Test
    @DisplayName("Should save, index and publish events when creating a valid event")
    void givenValidRequest_whenCreate_thenSaveIndexAndPublishEvents() throws Exception {
        UUID userId = UUID.randomUUID();
        CreateEventRequest request = new CreateEventRequest(
            "Test Event", "Desc", null, UUID.randomUUID(), "Concert",
            BookingModel.SEAT, Instant.now(), Instant.now().plusSeconds(7200),
            List.of(), List.of());
        Event event = Event.builder().id(UUID.randomUUID()).title("Test Event").bookingModel(BookingModel.SEAT).build();
        MultipartFile poster = org.mockito.Mockito.mock(MultipartFile.class);
        MultipartFile banner = org.mockito.Mockito.mock(MultipartFile.class);

        when(eventMapper.buildEvent(userId, request, null, null)).thenReturn(event);

        eventService.create(userId, request, poster, banner);

        verify(eventRepository).save(event);
        verify(eventSearchService).indexEvent(event);
        verify(outboxWriter).save(any(AuditEvent.class), eq(AUDIT_EVENT), anyString());
        verify(outboxWriter).save(any(EventCreatedEvent.class), eq(EVENT_CREATED), anyString());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when event not found by ID")
    void givenNonExistentId_whenGetById_thenThrowResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(eventRepository.findEventById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.getById(id))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should include locked seat IDs when event has seat model with locks")
    void givenSeatModelWithLocks_whenGetById_thenIncludeLockedSeatIds() {
        UUID id = UUID.randomUUID();
        Section section = Section.builder()
            .id(UUID.randomUUID()).build();
        section.setSeats(new java.util.LinkedHashSet<>(List.of(
            Seat.builder().id(UUID.randomUUID()).build()
        )));
        Event event = Event.builder()
            .id(id)
            .bookingModel(BookingModel.SEAT)
            .sections(List.of(section))
            .build();
        List<Seat> seats = new java.util.ArrayList<>(section.getSeats());
        when(seatRepository.findBySectionIdIn(List.of(section.getId()))).thenReturn(seats);
        when(eventRepository.findEventById(id)).thenReturn(Optional.of(event));

        eventService.getById(id);

        verify(seatLockRepository).findBySeatIdInAndExpiresAtAfter(any(), any());
        verify(eventMapper).toEventFullResponse(eq(event), any(Set.class));
    }

    @Test
    @DisplayName("Should return empty locked set when seat model has no seats")
    void givenSeatModelWithoutSeats_whenGetById_thenReturnEmptyLockedSet() {
        UUID id = UUID.randomUUID();
        Event event = Event.builder().id(id).bookingModel(BookingModel.SEAT).build();
        when(eventRepository.findEventById(id)).thenReturn(Optional.of(event));
        EventFullResponse expected = new EventFullResponse(
            id, "name", "desc", null, null, "cat", "org",
            "url","url", EventStatus.PUBLISHED, BookingModel.SEAT, Instant.now(), Instant.now(),
            List.of());
        when(eventMapper.toEventFullResponse(eq(event), eq(Set.of()))).thenReturn(expected);

        EventFullResponse result = eventService.getById(id);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("Should return without checking locks when event has zone model")
    void givenZoneModel_whenGetById_thenReturnWithoutCheckingLocks() {
        UUID id = UUID.randomUUID();
        Event event = Event.builder().id(id).bookingModel(BookingModel.ZONE).build();
        when(eventRepository.findEventById(id)).thenReturn(Optional.of(event));
        EventFullResponse expected = new EventFullResponse(
            id, "name", "desc", null, null, "cat", "org",
            "url","url", EventStatus.PUBLISHED, BookingModel.ZONE, Instant.now(), Instant.now(),
            List.of());
        when(eventMapper.toEventFullResponse(eq(event), eq(Set.of()))).thenReturn(expected);

        EventFullResponse result = eventService.getById(id);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("Should call user service and repository when fetching events")
    void givenValidUser_whenGetEvents_thenCallUserServiceAndRepository() {
        UUID userId = UUID.randomUUID();
        String org = "TestOrg";
        PageRequest pageable = PageRequest.of(0, 10);
        Event event = Event.builder().id(UUID.randomUUID()).title("Test").build();
        Page<Event> eventPage = new PageImpl<>(List.of(event));

        when(userServiceClient.getOrganizationName(userId)).thenReturn(org);
        when(eventRepository.findFilteredEvents(org, List.of(EventStatus.PUBLISHED, EventStatus.ACTIVE), pageable))
            .thenReturn(eventPage);

        Page<EventCardResponse> result = eventService.getEvents(userId, pageable);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("Should cancel event and publish events when event is published and far away")
    void givenPublishedEventFarAway_whenCancelEvent_thenSetCancelledAndPublishEvents() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Event event = Event.builder()
            .id(eventId)
            .status(EventStatus.PUBLISHED)
            .startDate(Instant.now().plusSeconds(86400 * 2))
            .build();

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        eventService.cancelEvent(eventId, userId);

        assertThat(event.getStatus()).isEqualTo(EventStatus.CANCELLED);
        verify(eventSearchService).deleteFromIndex(event);
        verify(eventRepository).save(event);
        verify(outboxWriter).save(any(AuditEvent.class), eq(AUDIT_EVENT), anyString());
        verify(outboxWriter).save(any(EventCancelledEvent.class), eq(EVENT_CANCELLED), anyString());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when cancelling a non-existent event")
    void givenNonExistentEvent_whenCancelEvent_thenThrowResourceNotFound() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.cancelEvent(eventId, UUID.randomUUID()))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should throw ConflictException when cancelling a non-published event")
    void givenEventNotPublished_whenCancelEvent_thenThrowConflict() {
        UUID eventId = UUID.randomUUID();
        Event event = Event.builder().id(eventId).status(EventStatus.ACTIVE).build();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> eventService.cancelEvent(eventId, UUID.randomUUID()))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("Should throw ConflictException when cancelling event less than 24 hours before start")
    void givenEventStartingSoon_whenCancelEvent_thenThrowConflict() {
        UUID eventId = UUID.randomUUID();
        Event event = Event.builder()
            .id(eventId)
            .status(EventStatus.PUBLISHED)
            .startDate(Instant.now().plusSeconds(3600))
            .build();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> eventService.cancelEvent(eventId, UUID.randomUUID()))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("Should check locks when seat model has sections with seats")
    void givenSeatModelWithSectionsAndSeats_whenGetById_thenCheckLocks() {
        UUID id = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        Section section = Section.builder()
            .id(UUID.randomUUID())
            .build();
        section.setSeats(new java.util.LinkedHashSet<>(List.of(
            Seat.builder().id(seatId).build()
        )));
        Event event = Event.builder()
            .id(id)
            .bookingModel(BookingModel.SEAT)
            .sections(List.of(section))
            .build();

        List<Seat> seats = new java.util.ArrayList<>(section.getSeats());
        when(seatRepository.findBySectionIdIn(List.of(section.getId()))).thenReturn(seats);
        when(eventRepository.findEventById(id)).thenReturn(Optional.of(event));
        when(seatLockRepository.findBySeatIdInAndExpiresAtAfter(
            eq(List.of(seatId)), any())).thenReturn(List.of());

        eventService.getById(id);

        verify(seatLockRepository).findBySeatIdInAndExpiresAtAfter(eq(List.of(seatId)), any());
        verify(eventMapper).toEventFullResponse(eq(event), eq(Set.of()));
    }

    @Test
    @DisplayName("Should activate event when it exists and is PUBLISHED")
    void givenPublishedEvent_whenActivateEvent_thenSetActiveAndPublishEvent() {
        UUID eventId = UUID.randomUUID();
        Event event = Event.builder().id(eventId).status(EventStatus.PUBLISHED).build();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        eventService.activateEvent(eventId);

        assertThat(event.getStatus()).isEqualTo(EventStatus.ACTIVE);
        verify(eventRepository).save(event);
        verify(outboxWriter).save(any(EventActivatedEvent.class), eq(EVENT_ACTIVATED), anyString());
    }

    @Test
    @DisplayName("Should not activate event when it is not PUBLISHED")
    void givenNonPublishedEvent_whenActivateEvent_thenDoNothing() {
        UUID eventId = UUID.randomUUID();
        Event event = Event.builder().id(eventId).status(EventStatus.CANCELLED).build();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        eventService.activateEvent(eventId);

        verify(eventRepository, never()).save(any());
        verify(outboxWriter, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("Should do nothing when activating a non-existent event")
    void givenNonExistentEvent_whenActivateEvent_thenDoNothing() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        eventService.activateEvent(eventId);

        verify(eventRepository, never()).save(any());
        verify(outboxWriter, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("Should complete event and release payout when it exists and is ACTIVE")
    void givenActiveEvent_whenCompleteEvent_thenSetCompletedAndPublishEvents() {
        UUID eventId = UUID.randomUUID();
        Event event = Event.builder().id(eventId).status(EventStatus.ACTIVE).organization("Org").build();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        eventService.completeEvent(eventId);

        assertThat(event.getStatus()).isEqualTo(EventStatus.COMPLETED);
        verify(eventRepository).save(event);
        verify(outboxWriter).save(any(EventCompletedEvent.class), eq(EVENT_COMPLETED), anyString());
        verify(outboxWriter).save(any(EventPayoutReleaseEvent.class), eq(EVENT_PAYOUT_RELEASED), anyString());
    }

    @Test
    @DisplayName("Should not complete event when it is not ACTIVE")
    void givenNonActiveEvent_whenCompleteEvent_thenDoNothing() {
        UUID eventId = UUID.randomUUID();
        Event event = Event.builder().id(eventId).status(EventStatus.PUBLISHED).build();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        eventService.completeEvent(eventId);

        verify(eventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should do nothing when completing a non-existent event")
    void givenNonExistentEvent_whenCompleteEvent_thenDoNothing() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        eventService.completeEvent(eventId);

        verify(eventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should complete event directly and release payout when PUBLISHED")
    void givenPublishedEvent_whenCompleteEventDirectly_thenSetCompletedAndReleasePayout() {
        UUID eventId = UUID.randomUUID();
        Event event = Event.builder().id(eventId).status(EventStatus.PUBLISHED).organization("Org").build();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        eventService.completeEventDirectly(eventId);

        assertThat(event.getStatus()).isEqualTo(EventStatus.COMPLETED);
        verify(eventRepository).save(event);
        verify(outboxWriter).save(any(EventPayoutReleaseEvent.class), eq(EVENT_PAYOUT_RELEASED), anyString());
    }

    @Test
    @DisplayName("Should not complete directly when event is not PUBLISHED")
    void givenNonPublishedEvent_whenCompleteEventDirectly_thenDoNothing() {
        UUID eventId = UUID.randomUUID();
        Event event = Event.builder().id(eventId).status(EventStatus.ACTIVE).build();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        eventService.completeEventDirectly(eventId);

        verify(eventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should do nothing when completing a non-existent event directly")
    void givenNonExistentEvent_whenCompleteEventDirectly_thenDoNothing() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        eventService.completeEventDirectly(eventId);

        verify(eventRepository, never()).save(any());
    }
}
