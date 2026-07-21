package org.ticketsouq.eventservice.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.ticketsouq.eventservice.model.*;
import org.ticketsouq.eventservice.model.enums.BookingModel;
import org.ticketsouq.eventservice.model.enums.EventStatus;
import org.ticketsouq.eventservice.model.enums.SeatStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoryTests extends RepositoryTestBase {

    @Autowired
    private EventCategoryRepository eventCategoryRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private SectionRepository sectionRepository;
    @Autowired
    private SeatRepository seatRepository;
    @Autowired
    private SeatLockRepository seatLockRepository;
    @Autowired
    private ZoneLockRepository zoneLockRepository;

    @Nested
    class EventTests {

        private EventCategory category;

        @BeforeEach
        void setUp() {
            category = eventCategoryRepository.save(EventCategory.builder().name("Music").build());
        }

        @Test
        void givenEventWithSectionsAndSeats_whenFindEventById_thenEagerFetch() {
            Event event = eventRepository.save(createEvent("Concert", "DefaultOrg"));
            Section section = Section.builder()
                .id(UUID.randomUUID())
                .event(event)
                .name("VIP")
                .capacity(10)
                .remainingCapacity(10)
                .build();
            event.setSections(new ArrayList<>(List.of(section)));
            Seat seat = Seat.builder().id(UUID.randomUUID()).section(section)
                .row(1).col(1).lable("A1").status(SeatStatus.AVAILABLE).build();
            section.setSeats(new ArrayList<>(List.of(seat)));
            eventRepository.save(event);

            Optional<Event> found = eventRepository.findEventById(event.getId());
            assertThat(found).isPresent();
            assertThat(found.get().getSections()).hasSize(1);
            assertThat(found.get().getSections().getFirst().getSeats()).hasSize(1);
        }

        @Test
        void givenEventsWithDifferentOrganizations_whenFindFilteredEvents_thenFilterByOrg() {
            eventRepository.save(createEvent("Event A", "Org1"));
            eventRepository.save(createEvent("Event B", "Org2"));
            Page<Event> result = eventRepository.findFilteredEvents(
                "Org1", List.of(EventStatus.PUBLISHED), PageRequest.of(0, 10));
            assertThat(result).hasSize(1);
            assertThat(result.getContent().getFirst().getTitle()).isEqualTo("Event A");
        }

        @Test
        void givenEventsWithDifferentStatuses_whenFindFilteredEventsWithNullOrg_thenFilterByStatuses() {
            Event published = createEvent("Published", "Org1");
            published.setStatus(EventStatus.PUBLISHED);
            Event active = createEvent("Active", "Org2");
            active.setStatus(EventStatus.ACTIVE);
            eventRepository.save(published);
            eventRepository.save(active);
            Page<Event> result = eventRepository.findFilteredEvents(
                null, List.of(EventStatus.PUBLISHED), PageRequest.of(0, 10));
            assertThat(result).hasSize(1);
        }

        @Test
        void givenEventsWithDifferentDates_whenFindFilteredEvents_thenOrderByStartDate() {
            Event early = createEvent("Early", "Org1");
            early.setStartDate(Instant.parse("2026-06-01T18:00:00Z"));
            Event late = createEvent("Late", "Org1");
            late.setStartDate(Instant.parse("2026-08-01T18:00:00Z"));
            eventRepository.save(early);
            eventRepository.save(late);
            Page<Event> result = eventRepository.findFilteredEvents(
                "Org1", List.of(EventStatus.PUBLISHED), PageRequest.of(0, 10));
            assertThat(result.getContent().get(0).getTitle()).isEqualTo("Early");
            assertThat(result.getContent().get(1).getTitle()).isEqualTo("Late");
        }

        private Event createEvent(String title, String org) {
            return Event.builder().title(title).organization(org).eventCategory(category)
                .PosterUrl("http://example.com/poster.jpg").status(EventStatus.PUBLISHED)
                .bookingModel(BookingModel.SEAT)
                .startDate(Instant.now().plusSeconds(86400))
                .finishDate(Instant.now().plusSeconds(172800)).build();
        }
    }

    @Nested
    class SeatLockTests {

        private Seat seat;

        @BeforeEach
        void setUp() {
            EventCategory cat = eventCategoryRepository.save(EventCategory.builder().name("Music").build());
            Event event = eventRepository.save(Event.builder().title("Concert").eventCategory(cat)
                .PosterUrl("url").status(EventStatus.PUBLISHED).bookingModel(BookingModel.SEAT)
                .startDate(Instant.now().plusSeconds(86400))
                .finishDate(Instant.now().plusSeconds(172800)).build());
            Section section = sectionRepository.save(Section.builder().id(UUID.randomUUID()).event(event)
                .name("VIP").capacity(10).remainingCapacity(10).build());
            seat = seatRepository.save(Seat.builder().id(UUID.randomUUID()).section(section)
                .row(1).col(1).lable("A1").status(SeatStatus.AVAILABLE).build());
        }

        @Test
        void givenActiveLock_whenFindBySeatIdInAndExpiresAtAfter_thenFound() {
            seatLockRepository.save(SeatLock.builder().seatId(seat.getId()).reservationId("res-1")
                .expiresAt(LocalDateTime.now().plusMinutes(10)).build());
            assertThat(seatLockRepository.findBySeatIdInAndExpiresAtAfter(
                List.of(seat.getId()), LocalDateTime.now())).hasSize(1);
        }

        @Test
        void givenExpiredLock_whenFindBySeatIdInAndExpiresAtAfter_thenNotFound() {
            seatLockRepository.save(SeatLock.builder().seatId(seat.getId()).reservationId("res-1")
                .expiresAt(LocalDateTime.now().minusMinutes(10)).build());
            assertThat(seatLockRepository.findBySeatIdInAndExpiresAtAfter(
                List.of(seat.getId()), LocalDateTime.now())).isEmpty();
        }

        @Test
        void givenLock_whenDeleteByReservationId_thenRemoved() {
            seatLockRepository.save(SeatLock.builder().seatId(seat.getId()).reservationId("res-1")
                .expiresAt(LocalDateTime.now().plusMinutes(10)).build());
            seatLockRepository.deleteByReservationId("res-1");
            assertThat(seatLockRepository.findByReservationId("res-1")).isEmpty();
        }

        @Test
        void givenExpiredLock_whenDeleteByExpiresAtBefore_thenDeleted() {
            seatLockRepository.save(SeatLock.builder().seatId(seat.getId()).reservationId("res-1")
                .expiresAt(LocalDateTime.now().minusMinutes(10)).build());
            assertThat(seatLockRepository.deleteByExpiresAtBefore(LocalDateTime.now(), 100)).isEqualTo(1);
        }

        @Test
        void givenActiveLock_whenDeleteByExpiresAtBefore_thenNotDeleted() {
            seatLockRepository.save(SeatLock.builder().seatId(seat.getId()).reservationId("res-1")
                .expiresAt(LocalDateTime.now().plusMinutes(10)).build());
            assertThat(seatLockRepository.deleteByExpiresAtBefore(LocalDateTime.now(), 100)).isEqualTo(0);
        }

        @Test
        void givenMultipleExpiredLocks_whenDeleteByExpiresAtBefore_thenRespectLimit() {
            Seat seat2 = seatRepository.save(Seat.builder().id(UUID.randomUUID()).section(seat.getSection())
                .row(1).col(2).lable("A2").status(SeatStatus.AVAILABLE).build());
            seatLockRepository.save(SeatLock.builder().seatId(seat.getId()).reservationId("res-1")
                .expiresAt(LocalDateTime.now().minusMinutes(10)).build());
            seatLockRepository.save(SeatLock.builder().seatId(seat2.getId()).reservationId("res-2")
                .expiresAt(LocalDateTime.now().minusMinutes(10)).build());
            assertThat(seatLockRepository.deleteByExpiresAtBefore(LocalDateTime.now(), 1)).isEqualTo(1);
        }
    }

    @Nested
    class ZoneLockTests {

        @Test
        void givenActiveLocks_whenSumActiveQuantityByZoneId_thenReturnTotal() {
            UUID zoneId = UUID.randomUUID();
            zoneLockRepository.save(ZoneLock.builder().zoneId(zoneId).reservationId("res-1").quantity(3)
                .expiresAt(LocalDateTime.now().plusMinutes(10)).build());
            zoneLockRepository.save(ZoneLock.builder().zoneId(zoneId).reservationId("res-2").quantity(2)
                .expiresAt(LocalDateTime.now().plusMinutes(10)).build());
            assertThat(zoneLockRepository.sumActiveQuantityByZoneId(zoneId, LocalDateTime.now())).isEqualTo(5);
        }

        @Test
        void givenExpiredLock_whenSumActiveQuantityByZoneId_thenExclude() {
            UUID zoneId = UUID.randomUUID();
            zoneLockRepository.save(ZoneLock.builder().zoneId(zoneId).reservationId("res-1").quantity(3)
                .expiresAt(LocalDateTime.now().minusMinutes(10)).build());
            assertThat(zoneLockRepository.sumActiveQuantityByZoneId(zoneId, LocalDateTime.now())).isEqualTo(0);
        }

        @Test
        void givenLock_whenFindByReservationId_thenFound() {
            zoneLockRepository.save(ZoneLock.builder().zoneId(UUID.randomUUID()).reservationId("res-1").quantity(2)
                .expiresAt(LocalDateTime.now().plusMinutes(10)).build());
            assertThat(zoneLockRepository.findByReservationId("res-1")).isPresent();
        }

        @Test
        void givenLock_whenFindByReservationIdWithLock_thenFound() {
            zoneLockRepository.save(ZoneLock.builder().zoneId(UUID.randomUUID()).reservationId("res-1").quantity(2)
                .expiresAt(LocalDateTime.now().plusMinutes(10)).build());
            assertThat(zoneLockRepository.findByReservationIdWithLock("res-1")).isPresent();
        }

        @Test
        void givenLock_whenDeleteByReservationId_thenRemoved() {
            zoneLockRepository.save(ZoneLock.builder().zoneId(UUID.randomUUID()).reservationId("res-1").quantity(2)
                .expiresAt(LocalDateTime.now().plusMinutes(10)).build());
            zoneLockRepository.deleteByReservationId("res-1");
            assertThat(zoneLockRepository.findByReservationId("res-1")).isEmpty();
        }

        @Test
        void givenExpiredLock_whenDeleteByExpiresAtBefore_thenDeleted() {
            zoneLockRepository.save(ZoneLock.builder().zoneId(UUID.randomUUID()).reservationId("res-1").quantity(2)
                .expiresAt(LocalDateTime.now().minusMinutes(10)).build());
            assertThat(zoneLockRepository.deleteByExpiresAtBefore(LocalDateTime.now(), 100)).isEqualTo(1);
        }

        @Test
        void givenActiveLock_whenDeleteByExpiresAtBefore_thenNotDeleted() {
            zoneLockRepository.save(ZoneLock.builder().zoneId(UUID.randomUUID()).reservationId("res-1").quantity(2)
                .expiresAt(LocalDateTime.now().plusMinutes(10)).build());
            assertThat(zoneLockRepository.deleteByExpiresAtBefore(LocalDateTime.now(), 100)).isEqualTo(0);
        }
    }
}
