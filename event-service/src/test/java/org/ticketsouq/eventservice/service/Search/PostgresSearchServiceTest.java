package org.ticketsouq.eventservice.service.Search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.ticketsouq.eventservice.dto.EventSearchRequest;
import org.ticketsouq.eventservice.dto.FrontendMap.EventCardResponse;
import org.ticketsouq.eventservice.model.Event;
import org.ticketsouq.eventservice.model.enums.EventStatus;
import org.ticketsouq.eventservice.repository.EventRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostgresSearchServiceTest {

    @Mock private EventRepository eventRepository;

    private PostgresSearchService postgresSearchService;

    @BeforeEach
    void setUp() {
        postgresSearchService = new PostgresSearchService(eventRepository);
    }

    @Test
    @DisplayName("Should delegate search to repository and map results")
    void givenSearchRequest_whenSearchBy_thenDelegateToRepository() {
        EventSearchRequest request = new EventSearchRequest("Concert", "LiveNation", "Music");
        Pageable pageable = PageRequest.of(0, 10);

        Event event = Event.builder()
            .id(UUID.randomUUID()).title("Concert").status(EventStatus.PUBLISHED)
            .startDate(Instant.now()).PosterUrl("/poster.jpg").build();
        Page<Event> repoPage = new PageImpl<>(List.of(event));

        when(eventRepository.searchBy("Concert", "LiveNation", "Music", pageable))
            .thenReturn(repoPage);

        Page<EventCardResponse> result = postgresSearchService.searchBy(request, pageable);

        assertThat(result).hasSize(1);
        assertThat(result.getContent().getFirst().id()).isEqualTo(event.getId());
        assertThat(result.getContent().getFirst().title()).isEqualTo("Concert");
        verify(eventRepository).searchBy("Concert", "LiveNation", "Music", pageable);
    }

    @Test
    @DisplayName("Should return empty page when repository returns no results")
    void givenNoResults_whenSearchBy_thenReturnEmptyPage() {
        EventSearchRequest request = new EventSearchRequest("NonExistent", null, null);
        Pageable pageable = PageRequest.of(0, 10);

        when(eventRepository.searchBy("NonExistent", null, null, pageable))
            .thenReturn(Page.empty());

        Page<EventCardResponse> result = postgresSearchService.searchBy(request, pageable);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should handle null params in search delegation")
    void givenNullParams_whenSearchBy_thenDelegateWithNulls() {
        EventSearchRequest request = new EventSearchRequest(null, null, null);
        Pageable pageable = PageRequest.of(0, 10);

        when(eventRepository.searchBy(null, null, null, pageable))
            .thenReturn(Page.empty());

        postgresSearchService.searchBy(request, pageable);

        verify(eventRepository).searchBy(null, null, null, pageable);
    }

    @Test
    @DisplayName("Index event should be a no-op")
    void givenAnyEvent_whenIndexEvent_thenDoNothing() {
        Event event = Event.builder().id(UUID.randomUUID()).build();

        postgresSearchService.indexEvent(event);

        verifyNoInteractions(eventRepository);
    }

    @Test
    @DisplayName("Delete from index should be a no-op")
    void givenAnyEvent_whenDeleteFromIndex_thenDoNothing() {
        Event event = Event.builder().id(UUID.randomUUID()).build();

        postgresSearchService.deleteFromIndex(event);

        verifyNoInteractions(eventRepository);
    }
}
