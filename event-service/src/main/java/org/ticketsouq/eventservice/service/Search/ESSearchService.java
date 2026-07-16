package org.ticketsouq.eventservice.service.Search;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.ticketsouq.eventservice.dto.EventSearchRequest;
import org.ticketsouq.eventservice.dto.FrontendMap.EventCardResponse;
import org.ticketsouq.eventservice.model.Event;
import org.ticketsouq.eventservice.repository.ElasticsearchEventRepository;
import org.ticketsouq.eventservice.repository.EventRepository;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ESSearchService implements SearchService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchEventRepository elasticsearchEventRepository;
    private final EventRepository eventRepository;

    public Page<EventCardResponse> searchBy(EventSearchRequest request, Pageable pageable) {
        if (request.title() == null && request.organization() == null && request.category() == null) {
            return Page.empty();
        }

        BoolQuery.Builder boolQuery = new BoolQuery.Builder();

        if (request.title() != null) addFilterLayer(boolQuery,"title",request.title());
        if (request.organization() != null) addFilterLayer(boolQuery,"organization",request.organization());
        if (request.category() != null) addFilterLayer(boolQuery,"category",request.category());

        var query = NativeQuery.builder()
            .withQuery(q -> q.bool(boolQuery.build()))
            .withPageable(pageable)
            .build();

        SearchHits<EventIndex> searchHits = elasticsearchOperations.search(query, EventIndex.class);

        List<UUID> matchingIds = searchHits.stream()
            .map(SearchHit::getContent)
            .map(EventIndex::getId)
            .toList();
        List<EventCardResponse> events = eventRepository.findAllById(matchingIds).stream()
            .map(EventCardResponse::from)
            .sorted(Comparator.comparing(EventCardResponse::startDate))
            .toList();


        return new PageImpl<>(events, pageable, searchHits.getTotalHits());
    }

    private void addFilterLayer(BoolQuery.Builder boolQuery,String field, String value) {
        boolQuery.must(new MatchQuery.Builder().field(field).query(value).fuzziness("AUTO").build()._toQuery());
    }

    public void indexEvent(Event event) {
        EventIndex index = EventIndex.builder()
            .id(event.getId())
            .title(event.getTitle())
            .category(event.getEventCategory().getName())
            .organization(event.getOrganization())
            .build();
        elasticsearchEventRepository.save(index);
    }

    public void deleteFromIndex(Event event) {
        elasticsearchEventRepository.deleteById(event.getId());
    }


}
