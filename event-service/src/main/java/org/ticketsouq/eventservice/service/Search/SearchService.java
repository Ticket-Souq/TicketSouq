package org.ticketsouq.eventservice.service.Search;

import org.springframework.data.domain.Pageable;
import org.ticketsouq.eventservice.dto.FrontendMap.EventCardResponse;
import org.ticketsouq.eventservice.model.Event;

import java.util.List;

public interface SearchService {

    public List<EventCardResponse> searchByTitle(String title, Pageable pageable);

    public void indexEvent(Event event);

    public void deleteFromIndex(Event event);
}
