package org.ticketsouq.eventservice.service;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.eventservice.dto.CreateSectionRequest;
import org.ticketsouq.eventservice.dto.SectionResponse;
import org.ticketsouq.eventservice.dto.UpdateSectionRequest;
import org.ticketsouq.eventservice.model.Event;
import org.ticketsouq.eventservice.model.Section;
import org.ticketsouq.eventservice.model.enums.BookingModel;
import org.ticketsouq.eventservice.model.enums.EventStatus;
import org.ticketsouq.eventservice.repository.EventRepository;
import org.ticketsouq.eventservice.repository.SectionRepository;
import org.ticketsouq.outbox.service.OutboxWriter;
import org.ticketsouq.sharedmodule.AuditService.events.AuditEvent;
import org.ticketsouq.sharedmodule.GeneralExceptions.BadRequestException;
import org.ticketsouq.sharedmodule.GeneralExceptions.ConflictException;
import org.ticketsouq.sharedmodule.GeneralExceptions.ResourceNotFoundException;

import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.AUDIT_EVENT;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SectionService {

    private final SectionRepository sectionRepository;
    private final EventRepository eventRepository;
    private final OutboxWriter outboxWriter;

    @Transactional
    public SectionResponse organizerReserve(UUID sectionId, UUID userId) {
        Section section = sectionRepository.findById(sectionId).orElseThrow(() -> new ResourceNotFoundException("Section not found.", sectionId));
        Event event = section.getEvent();
        validateEventCanBeUpdated(event);
        if (event.getBookingModel() != BookingModel.ZONE) {
            throw new BadRequestException("Organizer reserve is only available for zone-based events.");
        }
        if (section.getRemainingCapacity() < 1) {
            throw new ConflictException("No remaining capacity in this section.");
        }
        section.setRemainingCapacity(section.getRemainingCapacity() - 1);
        outboxWriter.save(new AuditEvent("Organizer reserved 1 ticket in section", userId, "", Instant.now()), AUDIT_EVENT, userId.toString());
        return SectionResponse.from(sectionRepository.save(section));
    }

    @Transactional
    public SectionResponse organizerRelease(UUID sectionId, UUID userId) {
        Section section = sectionRepository.findById(sectionId).orElseThrow(() -> new ResourceNotFoundException("Section not found.", sectionId));
        Event event = section.getEvent();
        if (event.getBookingModel() != BookingModel.ZONE) {
            throw new BadRequestException("Organizer release is only available for zone-based events.");
        }
        section.setRemainingCapacity(section.getRemainingCapacity() + 1);
        outboxWriter.save(new AuditEvent("Organizer released 1 ticket in section", userId, "", Instant.now()), AUDIT_EVENT, userId.toString());
        return SectionResponse.from(sectionRepository.save(section));
    }

    @Transactional
    public SectionResponse updateSection(UUID sectionId, UpdateSectionRequest request, UUID userId) {

        if (request.name() == null && request.price() == null && request.capacity() == null) {
            throw new BadRequestException("Nothing to update.");
        }

        Section section = sectionRepository.findById(sectionId).orElseThrow(() -> new ResourceNotFoundException("Section not found.", sectionId));

        Event event = section.getEvent();
        validateEventCanBeUpdated(event);

        if (event.getBookingModel() == BookingModel.ZONE) {
            updateZoneSection(section, request);
        } else {
            updateSeatSection(section, request);
        }
        outboxWriter.save(new AuditEvent("Section Status updated by Org Member", userId, "", Instant.now()), AUDIT_EVENT, userId.toString());

        return SectionResponse.from(sectionRepository.save(section));
    }

    private void updateZoneSection(Section section, UpdateSectionRequest request) {
        if (request.name() != null && !request.name().equals(section.getName())) {

            validateSectionName(section.getEvent().getId(), request.name());


            section.setName(request.name());
        }

        if (request.price() != null) {
            section.setPrice(request.price());
        }

        if (request.capacity() != null) {
            updateCapacity(section, request.capacity());
        }
    }


    @Transactional
    public SectionResponse createSection(UUID eventId, CreateSectionRequest request) {

        Event event = eventRepository.findById(eventId).orElseThrow(() -> new ResourceNotFoundException("Event not found.", eventId));


        validateEventCanBeUpdated(event);

        validateZoneBased(event);

        validateSectionName(eventId, request.name());

        Section section = Section.builder()
            .event(event)
            .name(request.name())
            .capacity(request.capacity())
            .remainingCapacity(request.capacity())
            .price(request.price())
            .build();

        return SectionResponse.from(sectionRepository.save(section));
    }

    private void validateSectionName(UUID eventId, String name) {

        if (sectionRepository.existsByEventIdAndName(eventId, name)) {

            throw new ConflictException("A section with this name already exists.");
        }
    }

    private void validateZoneBased(Event event) {

        if (event.getBookingModel() != BookingModel.ZONE) {
            throw new ConflictException("Sections can only be added to zone-based events.");
        }
    }

    private void updateSeatSection(Section section, UpdateSectionRequest request) {

        if (request.name() != null) {
            throw new ConflictException("Section name cannot be updated for seat-based events.");
        }

        if (request.capacity() != null) {
            throw new ConflictException("Capacity cannot be updated for seat-based events.");
        }

        if (request.price() != null) {
            section.setPrice(request.price());
        }

    }


    private void updateCapacity(Section section, Integer newCapacity) {

        int booked = section.getCapacity() - section.getRemainingCapacity();

        if (newCapacity < booked) {
            throw new ConflictException("Capacity cannot be less than booked seats.");
        }

        section.setCapacity(newCapacity);
        section.setRemainingCapacity(newCapacity - booked);
    }


    private void validateEventCanBeUpdated(Event event) {
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new ConflictException("Only published events can be updated.");
        }
    }
}
