package org.ticketsouq.eventservice.service.Search;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.ticketsouq.eventservice.dto.EventSearchRequest;
import org.ticketsouq.eventservice.dto.FrontendMap.EventCardResponse;
import org.ticketsouq.eventservice.model.Event;
import org.ticketsouq.eventservice.repository.EventRepository;

@Service
@RequiredArgsConstructor
public class PostgresSearchService implements SearchService {

    private final EventRepository eventRepository;

    public Page<EventCardResponse> searchBy(EventSearchRequest request, Pageable pageable) {
        return eventRepository.searchBy(request.title(), request.organization(), request.category(), pageable)
            .map(EventCardResponse::from);
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
