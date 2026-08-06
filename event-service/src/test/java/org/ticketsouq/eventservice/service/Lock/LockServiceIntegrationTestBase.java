package org.ticketsouq.eventservice.service.Lock;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.ticketsouq.eventservice.Client.UserServiceClient;
import org.ticketsouq.eventservice.EventServiceApplication;
import org.ticketsouq.eventservice.model.*;
import org.ticketsouq.eventservice.model.enums.BookingModel;
import org.ticketsouq.eventservice.model.enums.EventStatus;
import org.ticketsouq.eventservice.model.enums.SeatStatus;
import org.ticketsouq.eventservice.repository.*;
import org.ticketsouq.eventservice.repository.ElasticsearchEventRepository;
import org.ticketsouq.eventservice.service.LockService;
import org.ticketsouq.eventservice.service.Search.SearchService;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.time.Instant;

@SpringBootTest(classes = EventServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Sql(statements = """
    TRUNCATE TABLE zone_locks, seat_locks, seats, sections, events, event_categories CASCADE
    """, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
abstract class LockServiceIntegrationTestBase {

    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres")
        .withDatabaseName("testdb_app")
        .withUsername("test")
        .withPassword("test");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired protected LockService lockService;
    @Autowired protected EventRepository eventRepository;
    @Autowired protected SeatRepository seatRepository;
    @Autowired protected SectionRepository sectionRepository;
    @Autowired protected SeatLockRepository seatLockRepository;
    @Autowired protected ZoneLockRepository zoneLockRepository;
    @Autowired protected PlatformTransactionManager transactionManager;

    @MockitoBean protected UserServiceClient userServiceClient;
    @MockitoBean protected SearchService esSearchService;
    @MockitoBean protected ElasticsearchEventRepository elasticsearchEventRepository;
    @MockitoBean protected KafkaTemplate<String, Object> kafkaTemplate;

    @BeforeEach
    void cleanDatabase() {
        seatLockRepository.deleteAll();
        zoneLockRepository.deleteAll();
        seatRepository.deleteAll();
        sectionRepository.deleteAll();
        eventRepository.deleteAll();
    }

    protected Event createPublishedSeatEvent() {
        Event event = Event.builder()
            .title("Seat Event")
            .location("Test Location")
            .PosterUrl("http://example.com/poster.jpg")
            .status(EventStatus.PUBLISHED)
            .bookingModel(BookingModel.SEAT)
            .startDate(Instant.now().plus(Duration.ofDays(30)))
            .finishDate(Instant.now().plus(Duration.ofDays(31)))
            .build();
        return eventRepository.save(event);
    }

    protected Event createPublishedZoneEvent() {
        Event event = Event.builder()
            .title("Zone Event")
            .location("Test Location")
            .PosterUrl("http://example.com/poster.jpg")
            .status(EventStatus.PUBLISHED)
            .bookingModel(BookingModel.ZONE)
            .startDate(Instant.now().plus(Duration.ofDays(30)))
            .finishDate(Instant.now().plus(Duration.ofDays(31)))
            .build();
        return eventRepository.save(event);
    }

    protected Section createSection(Event event, int capacity) {
        Section section = Section.builder()
            .event(event)
            .name("VIP")
            .capacity(capacity)
            .remainingCapacity(capacity)
            .build();
        return sectionRepository.save(section);
    }

    protected Seat createSeat(Section section, SeatStatus status) {
        Seat seat = Seat.builder()
            .section(section)
            .lable("A1")
            .status(status)
            .build();
        return seatRepository.save(seat);
    }
}
