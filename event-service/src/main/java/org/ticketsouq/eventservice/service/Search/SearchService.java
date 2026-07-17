package org.ticketsouq.eventservice.service.Search;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.ticketsouq.eventservice.dto.EventSearchRequest;
import org.ticketsouq.eventservice.dto.FrontendMap.EventCardResponse;
import org.ticketsouq.eventservice.model.Event;

import java.util.List;

public interface SearchService {

    Page<EventCardResponse> searchBy(EventSearchRequest request , Pageable pageable);

    void indexEvent(Event event);

    void deleteFromIndex(Event event);
}
