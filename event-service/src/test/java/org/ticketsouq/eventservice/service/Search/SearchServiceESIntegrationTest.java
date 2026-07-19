package org.ticketsouq.eventservice.service.Search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;
import org.ticketsouq.eventservice.Client.UserServiceClient;
import org.ticketsouq.eventservice.EventServiceApplication;
import org.ticketsouq.eventservice.repository.RepositoryTestBase;
import org.ticketsouq.eventservice.dto.EventSearchRequest;
import org.ticketsouq.eventservice.dto.FrontendMap.EventCardResponse;
import org.ticketsouq.eventservice.model.Event;
import org.ticketsouq.eventservice.model.EventCategory;
import org.ticketsouq.eventservice.model.enums.BookingModel;
import org.ticketsouq.eventservice.model.enums.EventStatus;
import org.ticketsouq.eventservice.repository.EventCategoryRepository;
import org.ticketsouq.eventservice.repository.EventRepository;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = EventServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SearchServiceESIntegrationTest {

    @Container
    static ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
        DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:9.2.3")
            .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch"))
        .withEnv("xpack.security.enabled", "false")
        .withEnv("ES_JAVA_OPTS", "-Xms256m -Xmx256m")
        .withStartupTimeout(java.time.Duration.ofMinutes(2))
        .withReuse(true);

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test")
        .withReuse(true);

    static {
        elasticsearch.start();
        postgres.start();
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris", elasticsearch::getHttpHostAddress);
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.elasticsearch.connection-timeout", () -> "10s");
        registry.add("spring.elasticsearch.socket-timeout", () -> "30s");
    }

    @Resource(name = "ESSearchService")
    private ESSearchService esSearchService;
    @Autowired private EventRepository eventRepository;
    @Autowired private EventCategoryRepository eventCategoryRepository;
    @Autowired private ElasticsearchClient elasticsearchClient;

    @MockitoBean private UserServiceClient userServiceClient;
    @MockitoBean private KafkaTemplate<String, Object> kafkaTemplate;

    private EventCategory musicCategory;

    @BeforeEach
    void setUp() throws IOException {
        eventRepository.deleteAll();
        eventCategoryRepository.deleteAll();

        if (elasticsearchClient.indices().exists(e -> e.index("events")).value()) {
            elasticsearchClient.indices().delete(d -> d.index("events"));
        }
        elasticsearchClient.indices().create(c -> c
            .index("events")
            .mappings(m -> m
                .properties("id", p -> p.keyword(k -> k))
                .properties("title", p -> p.text(t -> t))
                .properties("organization", p -> p.text(t -> t))
                .properties("category", p -> p.text(t -> t))
            )
        );

        musicCategory = eventCategoryRepository.save(
            EventCategory.builder().name("Music").build());
    }

    @Test
    @DisplayName("Should index event and find it by title search")
    void givenIndexedEvent_whenSearchByTitle_thenReturnEvent() {
        Event event = createAndIndexEvent("Rock Concert", "LiveNation", musicCategory);

        Pageable pageable = PageRequest.of(0, 10);
        Page<EventCardResponse> result = esSearchService.searchBy(
            new EventSearchRequest("Rock Concert", null, null), pageable);

        assertThat(result).isNotEmpty();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().id()).isEqualTo(event.getId());
        assertThat(result.getContent().getFirst().title()).isEqualTo("Rock Concert");
    }

    @Test
    @DisplayName("Should find event by organization search")
    void givenIndexedEvent_whenSearchByOrganization_thenReturnEvent() {
        Event event = createAndIndexEvent("Summer Festival", "LiveNation", musicCategory);

        Pageable pageable = PageRequest.of(0, 10);
        Page<EventCardResponse> result = esSearchService.searchBy(
            new EventSearchRequest(null, "LiveNation", null), pageable);

        assertThat(result).isNotEmpty();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().id()).isEqualTo(event.getId());
    }

    @Test
    @DisplayName("Should find event by category search")
    void givenIndexedEvent_whenSearchByCategory_thenReturnEvent() {
        Event event = createAndIndexEvent("Jazz Night", "BlueNote", musicCategory);

        Pageable pageable = PageRequest.of(0, 10);
        Page<EventCardResponse> result = esSearchService.searchBy(
            new EventSearchRequest(null, null, "Music"), pageable);

        assertThat(result).isNotEmpty();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().id()).isEqualTo(event.getId());
    }

    @Test
    @DisplayName("Should find event with fuzzy matching on title")
    void givenIndexedEvent_whenSearchWithFuzzyTitle_thenReturnEvent() {
        Event event = createAndIndexEvent("Rock Concert", "LiveNation", musicCategory);

        Pageable pageable = PageRequest.of(0, 10);
        Page<EventCardResponse> result = esSearchService.searchBy(
            new EventSearchRequest("Rock Concrt", null, null), pageable);

        assertThat(result).isNotEmpty();
        assertThat(result.getContent().getFirst().id()).isEqualTo(event.getId());
    }

    @Test
    @DisplayName("Should return empty when no events match")
    void givenNoMatchingIndexedEvents_whenSearchBy_thenReturnEmpty() {
        createAndIndexEvent("Concert", "LiveNation", musicCategory);

        Pageable pageable = PageRequest.of(0, 10);
        Page<EventCardResponse> result = esSearchService.searchBy(
            new EventSearchRequest("NonexistentXYZ", null, null), pageable);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should return empty when all search params are null")
    void givenAllNullParams_whenSearchBy_thenReturnEmpty() {
        createAndIndexEvent("Concert", "LiveNation", musicCategory);

        Pageable pageable = PageRequest.of(0, 10);
        Page<EventCardResponse> result = esSearchService.searchBy(
            new EventSearchRequest(null, null, null), pageable);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should delete event from index and not find it anymore")
    void givenIndexedEvent_whenDeleteFromIndex_thenEventNotSearchable() {
        Event event = createAndIndexEvent("Concert", "LiveNation", musicCategory);

        esSearchService.deleteFromIndex(event);

        Pageable pageable = PageRequest.of(0, 10);
        Page<EventCardResponse> result = esSearchService.searchBy(
            new EventSearchRequest("Concert", null, null), pageable);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should find only matching events when multiple are indexed")
    void givenMultipleIndexedEvents_whenSearchBy_thenReturnOnlyMatching() {
        Event concert = createAndIndexEvent("Rock Concert", "LiveNation", musicCategory);
        createAndIndexEvent("Jazz Night", "BlueNote", musicCategory);
        createAndIndexEvent("Classical Music", "Philharmonic", musicCategory);

        Pageable pageable = PageRequest.of(0, 10);
        Page<EventCardResponse> result = esSearchService.searchBy(
            new EventSearchRequest("Rock Concert", null, null), pageable);

        assertThat(result).hasSize(1);
        assertThat(result.getContent().getFirst().id()).isEqualTo(concert.getId());
    }

    @Test
    @DisplayName("Should sort results by start date ascending")
    void givenMultipleMatchingEvents_whenSearchBy_thenSortByStartDate() {
        Event early = createAndIndexEvent("Concert",
            Instant.parse("2026-06-01T18:00:00Z"), musicCategory);
        Event late = createAndIndexEvent("Concert",
            Instant.parse("2026-08-15T18:00:00Z"), musicCategory);

        Pageable pageable = PageRequest.of(0, 10);
        Page<EventCardResponse> result = esSearchService.searchBy(
            new EventSearchRequest("Concert", null, null), pageable);

        assertThat(result).hasSize(2);
        assertThat(result.getContent().get(0).id()).isEqualTo(early.getId());
        assertThat(result.getContent().get(1).id()).isEqualTo(late.getId());
    }

    private Event createAndIndexEvent(String title, String organization, EventCategory category) {
        Event event = createEvent(title, organization, category,
            Instant.now().plusSeconds(86400));
        esSearchService.indexEvent(event);
        refreshESIndex();
        return event;
    }

    private Event createAndIndexEvent(String title, Instant startDate, EventCategory category) {
        Event event = createEvent(title, "Org", category, startDate);
        esSearchService.indexEvent(event);
        refreshESIndex();
        return event;
    }

    private Event createEvent(String title, String organization, EventCategory category,
                              Instant startDate) {
        Event event = Event.builder()
            .title(title)
            .organization(organization)
            .eventCategory(category)
            .PosterUrl("http://example.com/poster.jpg")
            .status(EventStatus.PUBLISHED)
            .bookingModel(BookingModel.SEAT)
            .startDate(startDate)
            .finishDate(startDate.plusSeconds(86400))
            .build();
        return eventRepository.save(event);
    }

    private void refreshESIndex() {
        try {
            elasticsearchClient.indices().refresh(r -> r.index("events"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to refresh ES index", e);
        }
    }
}
