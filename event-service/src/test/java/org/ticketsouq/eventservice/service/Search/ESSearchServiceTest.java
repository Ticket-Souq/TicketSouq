package org.ticketsouq.eventservice.service.Search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.ticketsouq.eventservice.dto.EventSearchRequest;
import org.ticketsouq.eventservice.dto.FrontendMap.EventCardResponse;
import org.ticketsouq.eventservice.model.Event;
import org.ticketsouq.eventservice.model.EventCategory;
import org.ticketsouq.eventservice.model.enums.EventStatus;
import org.ticketsouq.eventservice.repository.ElasticsearchEventRepository;
import org.ticketsouq.eventservice.repository.EventRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ESSearchServiceTest {

    @Mock private ElasticsearchOperations elasticsearchOperations;
    @Mock private ElasticsearchEventRepository elasticsearchEventRepository;
    @Mock private EventRepository eventRepository;
    @Mock private SearchHits<EventIndex> searchHits;

    private ESSearchService esSearchService;

    @BeforeEach
    void setUp() {
        esSearchService = new ESSearchService(elasticsearchOperations, elasticsearchEventRepository, eventRepository);
    }

    @Test
    @DisplayName("Should return empty page when all search params are null")
    void givenAllNullParams_whenSearchBy_thenReturnEmptyPage() {
        EventSearchRequest request = new EventSearchRequest(null, null, null);
        Pageable pageable = PageRequest.of(0, 10);

        Page<EventCardResponse> result = esSearchService.searchBy(request, pageable);

        assertThat(result).isEmpty();
        verifyNoInteractions(elasticsearchOperations);
    }

    @Test
    @DisplayName("Should search by title and return mapped results sorted by start date")
    void givenTitleParam_whenSearchBy_thenQueryESAndReturnResults() {
        EventSearchRequest request = new EventSearchRequest("Concert", null, null);
        Pageable pageable = PageRequest.of(0, 10);

        Event event1 = Event.builder()
            .id(UUID.randomUUID()).title("Concert A").status(EventStatus.PUBLISHED)
            .startDate(Instant.parse("2026-08-15T18:00:00Z")).PosterUrl("/poster1.jpg").build();
        Event event2 = Event.builder()
            .id(UUID.randomUUID()).title("Concert B").status(EventStatus.PUBLISHED)
            .startDate(Instant.parse("2026-07-15T18:00:00Z")).PosterUrl("/poster2.jpg").build();

        SearchHit<EventIndex> hit1 = mockSearchHit(event1.getId());
        SearchHit<EventIndex> hit2 = mockSearchHit(event2.getId());

        when(searchHits.stream()).thenReturn(Stream.of(hit1, hit2));
        when(searchHits.getTotalHits()).thenReturn(2L);
        when(elasticsearchOperations.search(any(NativeQuery.class), eq(EventIndex.class)))
            .thenReturn(searchHits);
        when(eventRepository.findAllById(List.of(event1.getId(), event2.getId())))
            .thenReturn(List.of(event1, event2));

        Page<EventCardResponse> result = esSearchService.searchBy(request, pageable);

        assertThat(result).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent().get(0).startDate()).isEqualTo(event2.getStartDate());
        assertThat(result.getContent().get(1).startDate()).isEqualTo(event1.getStartDate());
        verify(elasticsearchOperations).search(any(NativeQuery.class), eq(EventIndex.class));
    }

    @Test
    @DisplayName("Should search by organization")
    void givenOrganizationParam_whenSearchBy_thenQueryESWithOrganizationFilter() {
        EventSearchRequest request = new EventSearchRequest(null, "LiveNation", null);
        Pageable pageable = PageRequest.of(0, 10);

        when(searchHits.stream()).thenReturn(Stream.of());
        when(searchHits.getTotalHits()).thenReturn(0L);
        when(elasticsearchOperations.search(any(NativeQuery.class), eq(EventIndex.class)))
            .thenReturn(searchHits);

        Page<EventCardResponse> result = esSearchService.searchBy(request, pageable);

        assertThat(result).isEmpty();
        verify(eventRepository).findAllById(List.of());
    }

    @Test
    @DisplayName("Should search by category")
    void givenCategoryParam_whenSearchBy_thenQueryESWithCategoryFilter() {
        EventSearchRequest request = new EventSearchRequest(null, null, "Sports");
        Pageable pageable = PageRequest.of(0, 10);

        when(searchHits.stream()).thenReturn(Stream.of());
        when(searchHits.getTotalHits()).thenReturn(0L);
        when(elasticsearchOperations.search(any(NativeQuery.class), eq(EventIndex.class)))
            .thenReturn(searchHits);

        Page<EventCardResponse> result = esSearchService.searchBy(request, pageable);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should search with all three params")
    void givenAllParams_whenSearchBy_thenQueryESWithAllFilters() {
        EventSearchRequest request = new EventSearchRequest("Festival", "LiveNation", "Music");
        Pageable pageable = PageRequest.of(0, 10);

        when(searchHits.stream()).thenReturn(Stream.of());
        when(searchHits.getTotalHits()).thenReturn(0L);
        when(elasticsearchOperations.search(any(NativeQuery.class), eq(EventIndex.class)))
            .thenReturn(searchHits);

        Page<EventCardResponse> result = esSearchService.searchBy(request, pageable);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should index event by building EventIndex and saving to ES")
    void givenValidEvent_whenIndexEvent_thenBuildIndexAndSave() {
        EventCategory category = EventCategory.builder().id(UUID.randomUUID()).name("Music").build();
        Event event = Event.builder()
            .id(UUID.randomUUID())
            .title("Rock Concert")
            .organization("LiveNation")
            .eventCategory(category)
            .build();

        esSearchService.indexEvent(event);

        ArgumentCaptor<EventIndex> captor = ArgumentCaptor.forClass(EventIndex.class);
        verify(elasticsearchEventRepository).save(captor.capture());
        EventIndex saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(event.getId());
        assertThat(saved.getTitle()).isEqualTo("Rock Concert");
        assertThat(saved.getOrganization()).isEqualTo("LiveNation");
        assertThat(saved.getCategory()).isEqualTo("Music");
    }

    @Test
    @DisplayName("Should remove event from ES index")
    void givenValidEvent_whenDeleteFromIndex_thenDeleteById() {
        Event event = Event.builder().id(UUID.randomUUID()).build();

        esSearchService.deleteFromIndex(event);

        verify(elasticsearchEventRepository).deleteById(event.getId());
    }

    private SearchHit<EventIndex> mockSearchHit(UUID id) {
        EventIndex index = EventIndex.builder().id(id).build();
        SearchHit<EventIndex> hit = mock(SearchHit.class);
        when(hit.getContent()).thenReturn(index);
        return hit;
    }
}
