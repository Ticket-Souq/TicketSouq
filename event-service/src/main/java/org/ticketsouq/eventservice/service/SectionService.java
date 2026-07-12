package org.ticketsouq.eventservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.eventservice.dto.SectionRequest;
import org.ticketsouq.eventservice.dto.SectionResponse;
import org.ticketsouq.eventservice.model.Event;
import org.ticketsouq.eventservice.model.Section;
import org.ticketsouq.eventservice.repository.EventRepository;
import org.ticketsouq.eventservice.repository.SectionRepository;
import org.ticketsouq.sharedmodule.GeneralExceptions.ResourceNotFoundException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SectionService {

    private final SectionRepository sectionRepository;
    private final EventRepository eventRepository;

    @Transactional
    public SectionResponse create(UUID eventId, SectionRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId));
        Section section = Section.builder()
                .event(event)
                .name(request.name())
                .capacity(request.capacity())
                .remainingCapacity(request.remainingCapacity())
                .color(request.color())
                .price(request.price())
                .build();
        return SectionResponse.from(sectionRepository.save(section));
    }

    @Transactional(readOnly = true)
    public List<SectionResponse> getByEventId(UUID eventId) {
        return sectionRepository.findByEvent_Id(eventId).stream()
                .map(SectionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SectionResponse getById(UUID id) {
        return SectionResponse.from(sectionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Section", id)));
    }

    @Transactional
    public SectionResponse update(UUID id, SectionRequest request) {
        Section section = sectionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Section", id));
        section.setName(request.name());
        section.setCapacity(request.capacity());
        section.setRemainingCapacity(request.remainingCapacity());
        section.setColor(request.color());
        section.setPrice(request.price());
        return SectionResponse.from(sectionRepository.save(section));
    }

    @Transactional
    public void delete(UUID id) {
        Section section = sectionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Section", id));
        sectionRepository.delete(section);
    }

    @Transactional
    public void updateRemainingCapacity(UUID sectionId, Integer remainingCapacity) {
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Section", sectionId));
        section.setRemainingCapacity(remainingCapacity);
        sectionRepository.save(section);
    }
}
