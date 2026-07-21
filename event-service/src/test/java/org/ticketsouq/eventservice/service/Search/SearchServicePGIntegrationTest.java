package org.ticketsouq.eventservice.service.Search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.ticketsouq.eventservice.dto.EventSearchRequest;
import org.ticketsouq.eventservice.dto.FrontendMap.EventCardResponse;
import org.ticketsouq.eventservice.model.Event;
import org.ticketsouq.eventservice.model.EventCategory;
import org.ticketsouq.eventservice.model.enums.BookingModel;
import org.ticketsouq.eventservice.model.enums.EventStatus;
import org.ticketsouq.eventservice.repository.EventCategoryRepository;
import org.ticketsouq.eventservice.repository.EventRepository;
import org.ticketsouq.eventservice.repository.RepositoryTestBase;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SearchServicePGIntegrationTest extends RepositoryTestBase {

    @Autowired private EventRepository eventRepository;
    @Autowired private EventCategoryRepository eventCategoryRepository;

    private PostgresSearchService postgresSearchService;
    private EventCategory musicCategory;
    private EventCategory sportsCategory;

    @BeforeEach
    void setUp() {
        postgresSearchService = new PostgresSearchService(eventRepository);
        musicCategory = eventCategoryRepository.save(EventCategory.builder().name("Music").build());
        sportsCategory = eventCategoryRepository.save(EventCategory.builder().name("Sports").build());
    }

    @Test
    void givenEventsWithSimilarTitles_whenSearchByTitle_thenReturnMatchingEvents() {
        Event concert = createEvent("Rock Concert 2026", "LiveNation", musicCategory);
        createEvent("Summer Music Festival", "LiveNation", musicCategory);
        createEvent("Championship Game", "ESPN", sportsCategory);

        Page<EventCardResponse> result = searchByTitle("Concert");

        assertThat(result).isNotEmpty();
        assertThat(result.getContent()).extracting(EventCardResponse::id).contains(concert.getId());
    }

    @Test
    void givenEventsWithSimilarOrganizations_whenSearchByOrganization_thenReturnMatchingEvents() {
        Event event1 = createEvent("Event A", "LiveNation", musicCategory);
        Event event2 = createEvent("Event B", "LiveNation Inc", musicCategory);
        createEvent("Event C", "OtherOrg", sportsCategory);

        Page<EventCardResponse> result = postgresSearchService.searchBy(
            new EventSearchRequest(null, "LiveNation", null), PageRequest.of(0, 10));

        assertThat(result).isNotEmpty();
        assertThat(result.getContent()).extracting(EventCardResponse::id)
            .containsExactlyInAnyOrder(event1.getId(), event2.getId());
    }

    @Test
    void givenEventsWithSimilarCategories_whenSearchByCategory_thenReturnMatchingEvents() {
        Event event1 = createEvent("Concert A", "Org1", musicCategory);
        Event event2 = createEvent("Concert B", "Org2", musicCategory);
        createEvent("Game", "Org3", sportsCategory);

        Page<EventCardResponse> result = postgresSearchService.searchBy(
            new EventSearchRequest(null, null, "Music"), PageRequest.of(0, 10));

        assertThat(result).isNotEmpty();
        assertThat(result.getContent()).extracting(EventCardResponse::id)
            .containsExactlyInAnyOrder(event1.getId(), event2.getId());
    }

    @Test
    void givenEventsWithAllCriteria_whenSearchByAllParams_thenReturnMatchingEvents() {
        Event matching = createEvent("Rock Concert", "LiveNation", musicCategory);
        createEvent("Rock Concert", "OtherOrg", musicCategory);
        createEvent("Jazz Night", "LiveNation", musicCategory);

        Page<EventCardResponse> result = postgresSearchService.searchBy(
            new EventSearchRequest("Rock", "LiveNation", "Music"), PageRequest.of(0, 10));

        assertThat(result).isNotEmpty();
        assertThat(result.getContent()).extracting(EventCardResponse::id).containsExactly(matching.getId());
    }

    @Test
    void givenAllNullParams_whenSearchBy_thenReturnAllEvents() {
        createEvent("Event A", "Org1", musicCategory);
        createEvent("Event B", "Org2", sportsCategory);

        Page<EventCardResponse> result = postgresSearchService.searchBy(
            new EventSearchRequest(null, null, null), PageRequest.of(0, 10));

        assertThat(result).hasSize(2);
    }

    private Page<EventCardResponse> searchByTitle(String title) {
        return postgresSearchService.searchBy(
            new EventSearchRequest(title, null, null), PageRequest.of(0, 10));
    }

    private Event createEvent(String title, String organization, EventCategory category) {
        return eventRepository.save(Event.builder().title(title).organization(organization)
            .eventCategory(category).PosterUrl("http://example.com/poster.jpg")
            .status(EventStatus.PUBLISHED).bookingModel(BookingModel.SEAT)
            .startDate(Instant.now().plusSeconds(86400))
            .finishDate(Instant.now().plusSeconds(172800)).build());
    }
}
