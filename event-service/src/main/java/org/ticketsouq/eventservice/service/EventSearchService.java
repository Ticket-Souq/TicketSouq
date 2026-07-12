package org.ticketsouq.eventservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.ticketsouq.eventservice.dto.EventResponse;
import org.ticketsouq.eventservice.model.Event;
import org.ticketsouq.eventservice.model.enums.BookingModel;
import org.ticketsouq.eventservice.model.enums.EventStatus;
import org.ticketsouq.eventservice.repository.ElasticsearchEventRepository;
import org.ticketsouq.eventservice.repository.EventRepository;
import org.ticketsouq.eventservice.model.EventIndex;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EventSearchService {

    private final EventRepository eventRepository;
    private final ElasticsearchEventRepository elasticsearchEventRepository;

    public List<EventResponse> searchByTitleLike(String title, Pageable pageable) {
        return eventRepository.findByTitleContainingIgnoreCase(title,pageable).stream()
                .map(EventResponse::from)
                .toList();
    }

    public List<EventResponse> searchByTitleEs(String title) {
        return elasticsearchEventRepository.findByTitle(title).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<EventResponse> searchByTitleFuzzy(String title) {
        return elasticsearchEventRepository.findByTitleFuzzy(title).stream()
                .map(this::toResponse)
                .toList();
    }

    public void indexEvent(Event event) {
        EventIndex index = EventIndex.builder()
                .id(event.getId())
                .title(event.getTitle())
                .description(event.getDescription())
                .venueId(event.getVenueId())
                .organizationId(event.getOrganizationId())
                .createdBy(event.getCreatedBy())
                .posterUrl(event.getPosterUrl())
                .status(event.getStatus().name())
                .bookingModel(event.getBookingModel().name())
                .startDate(event.getStartDate())
                .finishDate(event.getFinishDate())
                .build();
        elasticsearchEventRepository.save(index);
    }

    public void deleteFromIndex(Event event) {
        elasticsearchEventRepository.deleteById(event.getId());
    }

    private EventResponse toResponse(EventIndex index) {
        return new EventResponse(
                index.getId(),
                index.getTitle(),
                index.getDescription(),
                index.getVenueId(),
                index.getOrganizationId(),
                index.getCreatedBy(),
                index.getPosterUrl(),
                EventStatus.valueOf(index.getStatus()),
                BookingModel.valueOf(index.getBookingModel()),
                index.getStartDate(),
                index.getFinishDate()
        );
    }
}
