//package org.ticketsouq.eventservice.service.Search;
//
//import lombok.RequiredArgsConstructor;
//import org.springframework.data.domain.Pageable;
//import org.springframework.stereotype.Service;
//import org.ticketsouq.eventservice.dto.FrontendMap.EventCardResponse;
//import org.ticketsouq.eventservice.model.Event;
//import org.ticketsouq.eventservice.repository.ElasticsearchEventRepository;
//import org.ticketsouq.eventservice.repository.EventRepository;
//
//import java.util.List;
//
//@Service
//@RequiredArgsConstructor
//public class ESSearchService implements SearchService {
//
//    private final ElasticsearchEventRepository elasticsearchEventRepository;
//    private final EventRepository eventRepository;
//
//
//    public List<EventCardResponse> searchByTitle(String title, Pageable pageable) {
//        return elasticsearchEventRepository.findByTitleFuzzy(title)
//            .stream()
//            .skip(pageable.getOffset())
//            .limit(pageable.getPageSize())
//            .map(this::getEntity)
//            .map(EventCardResponse::from)
//            .toList();
//    }
//
//    public void indexEvent(Event event) {
//        EventIndex index = EventIndex.builder()
//            .id(event.getId())
//            .title(event.getTitle())
//            .categoryName(event.getEventCategory().getName())
//            .organizationName(event.getOrganization())
//            .build();
//        elasticsearchEventRepository.save(index);
//    }
//
//    public void deleteFromIndex(Event event) {
//        elasticsearchEventRepository.deleteById(event.getId());
//    }
//
//
//    private Event getEntity(EventIndex index) {
//        return eventRepository.findById(index.getId()).orElseThrow();
//    }
//
//
//}
