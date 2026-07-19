package org.ticketsouq.eventservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.ticketsouq.eventservice.dto.CreateSectionRequest;
import org.ticketsouq.eventservice.dto.SectionResponse;
import org.ticketsouq.eventservice.dto.UpdateSectionRequest;
import org.ticketsouq.eventservice.model.Event;
import org.ticketsouq.eventservice.model.Section;
import org.ticketsouq.eventservice.model.enums.BookingModel;
import org.ticketsouq.eventservice.model.enums.EventStatus;
import org.ticketsouq.eventservice.repository.EventRepository;
import org.ticketsouq.eventservice.repository.SectionRepository;
import org.ticketsouq.sharedmodule.AuditService.events.AuditEvent;
import org.ticketsouq.sharedmodule.GeneralExceptions.BadRequestException;
import org.ticketsouq.sharedmodule.GeneralExceptions.ConflictException;
import org.ticketsouq.sharedmodule.GeneralExceptions.ResourceNotFoundException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SectionServiceTest {

    @Mock private SectionRepository sectionRepository;
    @Mock private EventRepository eventRepository;
    @Mock private ApplicationEventPublisher applicationEventPublisher;

    private SectionService sectionService;

    @BeforeEach
    void setUp() {
        sectionService = new SectionService(sectionRepository, eventRepository, applicationEventPublisher);
    }

    @Test
    @DisplayName("Should update all fields when zone-based section")
    void givenZoneBasedSection_whenUpdateWithAllFields_thenSucceed() {
        UUID sectionId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Event event = Event.builder()
            .id(eventId)
            .status(EventStatus.PUBLISHED)
            .bookingModel(BookingModel.ZONE)
            .build();
        Section section = Section.builder()
            .id(sectionId)
            .event(event)
            .name("Old Name")
            .price(BigDecimal.TEN)
            .capacity(100)
            .remainingCapacity(80)
            .build();
        UpdateSectionRequest request = UpdateSectionRequest.builder()
            .name("New Name")
            .price(BigDecimal.valueOf(20))
            .capacity(120)
            .build();

        when(sectionRepository.findById(sectionId)).thenReturn(Optional.of(section));
        when(sectionRepository.save(section)).thenReturn(section);

        SectionResponse response = sectionService.updateSection(sectionId, request, userId);

        assertThat(response.name()).isEqualTo("New Name");
        assertThat(response.capacity()).isEqualTo(120);
        assertThat(response.remainingCapacity()).isEqualTo(100);
        verify(applicationEventPublisher).publishEvent(any(AuditEvent.class));
    }

    @Test
    @DisplayName("Should update only price when seat-based section")
    void givenSeatBasedSection_whenUpdatePriceOnly_thenSucceed() {
        UUID sectionId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Event event = Event.builder()
            .id(eventId)
            .status(EventStatus.PUBLISHED)
            .bookingModel(BookingModel.SEAT)
            .build();
        Section section = Section.builder()
            .id(sectionId)
            .event(event)
            .name("Section A")
            .price(BigDecimal.TEN)
            .build();
        UpdateSectionRequest request = UpdateSectionRequest.builder()
            .price(BigDecimal.valueOf(25))
            .build();

        when(sectionRepository.findById(sectionId)).thenReturn(Optional.of(section));
        when(sectionRepository.save(section)).thenReturn(section);

        SectionResponse response = sectionService.updateSection(sectionId, request, userId);

        assertThat(response.name()).isEqualTo("Section A");
        verify(applicationEventPublisher).publishEvent(any(AuditEvent.class));
    }

    @Test
    @DisplayName("Should throw BadRequestException when nothing to update")
    void givenNoChanges_whenUpdateSection_thenThrowBadRequest() {
        UUID sectionId = UUID.randomUUID();
        UpdateSectionRequest request = UpdateSectionRequest.builder().build();

        assertThatThrownBy(() -> sectionService.updateSection(sectionId, request, UUID.randomUUID()))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when section not found")
    void givenNonExistentSection_whenUpdateSection_thenThrowResourceNotFound() {
        UUID sectionId = UUID.randomUUID();
        UpdateSectionRequest request = UpdateSectionRequest.builder().name("New").build();
        when(sectionRepository.findById(sectionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sectionService.updateSection(sectionId, request, UUID.randomUUID()))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should throw ConflictException when event is not published")
    void givenEventNotPublished_whenUpdateSection_thenThrowConflict() {
        UUID sectionId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Event event = Event.builder()
            .id(eventId)
            .status(EventStatus.ACTIVE)
            .bookingModel(BookingModel.ZONE)
            .build();
        Section section = Section.builder().id(sectionId).event(event).build();
        when(sectionRepository.findById(sectionId)).thenReturn(Optional.of(section));

        assertThatThrownBy(() -> sectionService.updateSection(
            sectionId, UpdateSectionRequest.builder().name("New").build(), UUID.randomUUID()))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("Should throw ConflictException when changing name for seat-based section")
    void givenSeatBasedSection_whenChangeName_thenThrowConflict() {
        UUID sectionId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Event event = Event.builder()
            .id(eventId)
            .status(EventStatus.PUBLISHED)
            .bookingModel(BookingModel.SEAT)
            .build();
        Section section = Section.builder().id(sectionId).event(event).build();
        when(sectionRepository.findById(sectionId)).thenReturn(Optional.of(section));

        assertThatThrownBy(() -> sectionService.updateSection(
            sectionId, UpdateSectionRequest.builder().name("New").build(), UUID.randomUUID()))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("Should throw ConflictException when changing capacity for seat-based section")
    void givenSeatBasedSection_whenChangeCapacity_thenThrowConflict() {
        UUID sectionId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Event event = Event.builder()
            .id(eventId)
            .status(EventStatus.PUBLISHED)
            .bookingModel(BookingModel.SEAT)
            .build();
        Section section = Section.builder().id(sectionId).event(event).build();
        when(sectionRepository.findById(sectionId)).thenReturn(Optional.of(section));

        assertThatThrownBy(() -> sectionService.updateSection(
            sectionId, UpdateSectionRequest.builder().capacity(100).build(), UUID.randomUUID()))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("Should throw ConflictException when new name duplicates an existing section")
    void givenDuplicateName_whenUpdateSection_thenThrowConflict() {
        UUID sectionId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Event event = Event.builder()
            .id(eventId)
            .status(EventStatus.PUBLISHED)
            .bookingModel(BookingModel.ZONE)
            .build();
        Section section = Section.builder()
            .id(sectionId)
            .event(event)
            .name("Old")
            .build();
        when(sectionRepository.findById(sectionId)).thenReturn(Optional.of(section));
        when(sectionRepository.existsByEventIdAndName(eventId, "Dup")).thenReturn(true);

        assertThatThrownBy(() -> sectionService.updateSection(
            sectionId, UpdateSectionRequest.builder().name("Dup").build(), UUID.randomUUID()))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("Should throw ConflictException when new capacity is less than already booked")
    void givenCapacityLessThanBooked_whenUpdateSection_thenThrowConflict() {
        UUID sectionId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Event event = Event.builder()
            .id(eventId)
            .status(EventStatus.PUBLISHED)
            .bookingModel(BookingModel.ZONE)
            .build();
        Section section = Section.builder()
            .id(sectionId)
            .event(event)
            .name("Test")
            .capacity(100)
            .remainingCapacity(60)
            .build();
        when(sectionRepository.findById(sectionId)).thenReturn(Optional.of(section));

        assertThatThrownBy(() -> sectionService.updateSection(
            sectionId, UpdateSectionRequest.builder().capacity(30).build(), UUID.randomUUID()))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("Should create section successfully for zone-based event")
    void givenZoneBasedEvent_whenCreateSection_thenSucceed() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Event event = Event.builder()
            .id(eventId)
            .status(EventStatus.PUBLISHED)
            .bookingModel(BookingModel.ZONE)
            .build();
        CreateSectionRequest request = new CreateSectionRequest("VIP", 100, BigDecimal.valueOf(50));

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(sectionRepository.save(any(Section.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SectionResponse response = sectionService.createSection(eventId, request);

        assertThat(response.name()).isEqualTo("VIP");
        assertThat(response.capacity()).isEqualTo(100);
        assertThat(response.remainingCapacity()).isEqualTo(100);
        verify(sectionRepository).save(any(Section.class));
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when creating section for non-existent event")
    void givenNonExistentEvent_whenCreateSection_thenThrowResourceNotFound() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sectionService.createSection(
            eventId, new CreateSectionRequest("VIP", 100, BigDecimal.TEN)))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should throw ConflictException when creating section for non-published event")
    void givenEventNotPublished_whenCreateSection_thenThrowConflict() {
        UUID eventId = UUID.randomUUID();
        Event event = Event.builder().id(eventId).status(EventStatus.ACTIVE).bookingModel(BookingModel.ZONE).build();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> sectionService.createSection(
            eventId, new CreateSectionRequest("VIP", 100, BigDecimal.TEN)))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("Should throw ConflictException when creating section for non-zone event")
    void givenSeatBasedEvent_whenCreateSection_thenThrowConflict() {
        UUID eventId = UUID.randomUUID();
        Event event = Event.builder().id(eventId).status(EventStatus.PUBLISHED).bookingModel(BookingModel.SEAT).build();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> sectionService.createSection(
            eventId, new CreateSectionRequest("VIP", 100, BigDecimal.TEN)))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("Should throw ConflictException when section name already exists")
    void givenDuplicateName_whenCreateSection_thenThrowConflict() {
        UUID eventId = UUID.randomUUID();
        Event event = Event.builder().id(eventId).status(EventStatus.PUBLISHED).bookingModel(BookingModel.ZONE).build();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(sectionRepository.existsByEventIdAndName(eventId, "VIP")).thenReturn(true);

        assertThatThrownBy(() -> sectionService.createSection(
            eventId, new CreateSectionRequest("VIP", 100, BigDecimal.TEN)))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("Should succeed when updating capacity to zero")
    void givenNewCapacityZero_whenUpdateSection_thenSucceed() {
        UUID sectionId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Event event = Event.builder()
            .id(eventId)
            .status(EventStatus.PUBLISHED)
            .bookingModel(BookingModel.ZONE)
            .build();
        Section section = Section.builder()
            .id(sectionId)
            .event(event)
            .name("Test")
            .price(BigDecimal.TEN)
            .capacity(100)
            .remainingCapacity(100)
            .build();
        UpdateSectionRequest request = UpdateSectionRequest.builder()
            .capacity(0)
            .build();

        when(sectionRepository.findById(sectionId)).thenReturn(Optional.of(section));
        when(sectionRepository.save(section)).thenReturn(section);

        SectionResponse response = sectionService.updateSection(sectionId, request, userId);

        assertThat(response.capacity()).isEqualTo(0);
        assertThat(response.remainingCapacity()).isEqualTo(0);
    }
}
