package org.ticketsouq.eventservice.service;


import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.eventservice.dto.SectionResponse;
import org.ticketsouq.eventservice.dto.UpdateSectionRequest;
import org.ticketsouq.eventservice.model.Event;
import org.ticketsouq.eventservice.model.Section;
import org.ticketsouq.eventservice.model.enums.BookingModel;
import org.ticketsouq.eventservice.model.enums.EventStatus;
import org.ticketsouq.eventservice.repository.SectionRepository;
import org.ticketsouq.sharedmodule.AuditService.events.AuditEvent;
import org.ticketsouq.sharedmodule.GeneralExceptions.BadRequestException;
import org.ticketsouq.sharedmodule.GeneralExceptions.ConflictException;
import org.ticketsouq.sharedmodule.GeneralExceptions.ResourceNotFoundException;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SectionService {

    private final SectionRepository sectionRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public SectionResponse updateSection(UUID sectionId, UpdateSectionRequest request, UUID userId) {

        if (request.name() == null && request.price() == null && request.capacity() == null) throw new BadRequestException("Nothing to update.");

        Section section = sectionRepository.findById(sectionId).orElseThrow(() -> new ResourceNotFoundException("Section not found.", sectionId));

        Event event = section.getEvent();
        validateEventCanBeUpdated(event);

        if (event.getBookingModel() == BookingModel.ZONE) {
            updateZoneSection(section, request);
        } else {
            updateSeatSection(section, request);
        }
        applicationEventPublisher.publishEvent(new AuditEvent("Section Status updated by Org Member", userId, "", Instant.now()));

        return SectionResponse.from(sectionRepository.save(section));
    }

    private void updateZoneSection(Section section, UpdateSectionRequest request) {
        if (request.name() != null
            && !request.name().equals(section.getName())) {

            if (sectionRepository.existsByEventIdAndName(
                section.getEvent().getId(),
                request.name())) {

                throw new ConflictException(
                    "A section with this name already exists."
                );
            }

            section.setName(request.name());
        }

        if (request.price() != null) section.setPrice(request.price());

        if (request.capacity() != null) updateCapacity(section, request.capacity());
    }


    private void updateSeatSection(Section section, UpdateSectionRequest request) {

        if (request.name() != null) throw new ConflictException("Section name cannot be updated for seat-based events.");

        if (request.capacity() != null) throw new ConflictException("Capacity cannot be updated for seat-based events.");

        if (request.price() != null) section.setPrice(request.price());

    }


    private void updateCapacity(Section section, Integer newCapacity) {

        int booked = section.getCapacity() - section.getRemainingCapacity();

        if (newCapacity < booked) throw new ConflictException("Capacity cannot be less than booked seats.");

        section.setCapacity(newCapacity);
        section.setRemainingCapacity(newCapacity - booked);
    }


    private void validateEventCanBeUpdated(Event event) {
        if (event.getStatus() != EventStatus.PUBLISHED) throw new ConflictException("Only published events can be updated.");
    }
}
