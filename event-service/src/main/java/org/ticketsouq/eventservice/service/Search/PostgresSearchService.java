package org.ticketsouq.eventservice.service.Search;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.ticketsouq.eventservice.dto.FrontendMap.EventCardResponse;
import org.ticketsouq.eventservice.model.Event;
import org.ticketsouq.eventservice.repository.EventRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PostgresSearchService implements SearchService {

    private final EventRepository eventRepository;

    public List<EventCardResponse> searchByTitle(String title, Pageable pageable) {
        return eventRepository.findFuzzyByTitleOrSectionName(title, pageable).stream()
            .map(EventCardResponse::from)
            .toList();
    }

    @Override
    public void indexEvent(Event event) {
        // do nothing
    }

    @Override
    public void deleteFromIndex(Event event) {
        // do nothing
    }
}
