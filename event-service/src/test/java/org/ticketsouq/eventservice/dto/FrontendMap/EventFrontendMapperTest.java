package org.ticketsouq.eventservice.dto.FrontendMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ticketsouq.eventservice.Client.UserServiceClient;
import org.ticketsouq.eventservice.model.Event;
import org.ticketsouq.eventservice.model.EventCategory;
import org.ticketsouq.eventservice.model.Seat;
import org.ticketsouq.eventservice.model.Section;
import org.ticketsouq.eventservice.model.enums.BookingModel;
import org.ticketsouq.eventservice.model.enums.EventStatus;
import org.ticketsouq.eventservice.model.enums.SeatStatus;
import org.ticketsouq.eventservice.repository.EventCategoryRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventFrontendMapperTest {

    @Mock
    private EventCategoryRepository eventCategoryRepository;
    @Mock
    private UserServiceClient userServiceClient;

    private EventFrontendMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new EventFrontendMapper(eventCategoryRepository, userServiceClient);
    }

    @Test
    @DisplayName("Should build SEAT event with sections and seats from request")
    void givenSeatRequest_whenBuildEvent_thenCreateEntityWithSectionsAndSeats() {
        UUID userId = UUID.randomUUID();
        UUID sectionId = UUID.randomUUID();
        UUID cellId = UUID.randomUUID();

        CreateEventWithLayoutRequest request = new CreateEventWithLayoutRequest(
            "SEAT", "Test Event", "Description", UUID.randomUUID(), "Concert",
            "http://example.com/poster.jpg", Instant.now(), Instant.now().plusSeconds(7200),
            List.of(new CreateEventWithLayoutRequest.RowDto(
                "row-1", "A", false,
                List.of(new CreateEventWithLayoutRequest.CellDto(
                    cellId.toString(), "seat", "A1", "available", sectionId.toString()
                ))
            )),
            List.of(new CreateEventWithLayoutRequest.CategoryDto(
                sectionId.toString(), "VIP", "red", 10, BigDecimal.valueOf(100)
            ))
        );

        when(eventCategoryRepository.findByNameIgnoreCase("Concert"))
            .thenReturn(Optional.of(EventCategory.builder().name("Concert").build()));
        when(userServiceClient.getOrganizationName(userId)).thenReturn("TestOrg");

        Event event = mapper.buildEvent(userId, request);

        assertThat(event.getTitle()).isEqualTo("Test Event");
        assertThat(event.getOrganization()).isEqualTo("TestOrg");
        assertThat(event.getBookingModel()).isEqualTo(BookingModel.SEAT);
        assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(event.getSections()).hasSize(1);
        assertThat(event.getSections().getFirst().getName()).isEqualTo("VIP");
        assertThat(event.getSections().getFirst().getSeats()).hasSize(1);
        assertThat(event.getSections().getFirst().getSeats().getFirst().getLable()).isEqualTo("A1");
    }

    @Test
    @DisplayName("Should build ZONE event without seats")
    void givenZoneRequest_whenBuildEvent_thenCreateEntityWithoutSeats() {
        UUID userId = UUID.randomUUID();
        UUID sectionId = UUID.randomUUID();

        CreateEventWithLayoutRequest request = new CreateEventWithLayoutRequest(
            "ZONE", "Zone Event", "Desc", UUID.randomUUID(), "Festival",
            "http://example.com/poster.jpg", Instant.now(), Instant.now().plusSeconds(7200),
            List.of(),
            List.of(new CreateEventWithLayoutRequest.CategoryDto(
                sectionId.toString(), "Gold", "gold", 100, BigDecimal.valueOf(200)
            ))
        );

        when(eventCategoryRepository.findByNameIgnoreCase("Festival"))
            .thenReturn(Optional.of(EventCategory.builder().name("Festival").build()));
        when(userServiceClient.getOrganizationName(userId)).thenReturn("Org");

        Event event = mapper.buildEvent(userId, request);

        assertThat(event.getBookingModel()).isEqualTo(BookingModel.ZONE);
        assertThat(event.getSections()).hasSize(1);
        assertThat(event.getSections().getFirst().getSeats()).isEmpty();
    }

    @Test
    @DisplayName("Should create new category when not found")
    void givenNewCategory_whenBuildEvent_thenCreateCategory() {
        UUID userId = UUID.randomUUID();
        UUID sectionId = UUID.randomUUID();

        CreateEventWithLayoutRequest request = new CreateEventWithLayoutRequest(
            "SEAT", "Event", null, UUID.randomUUID(), "NewCategory",
            "url", Instant.now(), Instant.now().plusSeconds(7200),
            List.of(), List.of(new CreateEventWithLayoutRequest.CategoryDto(
            sectionId.toString(), "General", "blue", 50, BigDecimal.TEN
        ))
        );

        EventCategory savedCategory = EventCategory.builder().id(UUID.randomUUID()).name("NewCategory").build();
        when(eventCategoryRepository.findByNameIgnoreCase("NewCategory")).thenReturn(Optional.empty());
        when(eventCategoryRepository.save(any(EventCategory.class))).thenReturn(savedCategory);
        when(userServiceClient.getOrganizationName(userId)).thenReturn("Org");

        Event event = mapper.buildEvent(userId, request);

        assertThat(event.getEventCategory().getName()).isEqualTo("NewCategory");
        verify(eventCategoryRepository).save(any(EventCategory.class));
    }

    @Test
    @DisplayName("Should map null mode to SEAT")
    void givenNullMode_whenBuildEvent_thenDefaultToSeat() {
        UUID userId = UUID.randomUUID();

        CreateEventWithLayoutRequest request = new CreateEventWithLayoutRequest(
            null, "Event", null, UUID.randomUUID(), "Cat",
            "url", Instant.now(), Instant.now().plusSeconds(7200),
            List.of(), List.of()
        );

        when(eventCategoryRepository.findByNameIgnoreCase("Cat"))
            .thenReturn(Optional.of(EventCategory.builder().name("Cat").build()));
        when(userServiceClient.getOrganizationName(userId)).thenReturn("Org");

        Event event = mapper.buildEvent(userId, request);

        assertThat(event.getBookingModel()).isEqualTo(BookingModel.SEAT);
    }

    @Test
    @DisplayName("Should convert SEAT event to EventLayoutResponse with rows and cells")
    void givenSeatEvent_whenToEventLayoutResponse_thenIncludeRowsAndCategories() {
        EventCategory category = EventCategory.builder().name("Concert").build();
        Event event = Event.builder()
            .id(UUID.randomUUID()).title("Test").description("Desc")
            .organization("Org").PosterUrl("url").status(EventStatus.PUBLISHED)
            .bookingModel(BookingModel.SEAT)
            .startDate(Instant.now()).finishDate(Instant.now().plusSeconds(7200))
            .eventCategory(category)
            .build();

        Section section = Section.builder()
            .id(UUID.randomUUID()).event(event).name("VIP")
            .capacity(10).remainingCapacity(8).color("red")
            .price(BigDecimal.valueOf(100)).build();
        Seat seat = Seat.builder()
            .id(UUID.randomUUID()).section(section).row(0).col(0)
            .lable("A1").status(SeatStatus.AVAILABLE).build();
        section.setSeats(List.of(seat));
        event.setSections(List.of(section));

        EventLayoutResponse response = mapper.toEventLayoutResponse(event, Set.of());

        assertThat(response.id()).isEqualTo(event.getId());
        assertThat(response.name()).isEqualTo("Test");
        assertThat(response.mode()).isEqualTo("SEAT_BASED");
        assertThat(response.categories()).hasSize(1);
        assertThat(response.categories().getFirst().name()).isEqualTo("VIP");
        assertThat(response.rows()).hasSize(1);
        assertThat(response.rows().getFirst().cells()).hasSize(1);
        assertThat(response.rows().getFirst().cells().getFirst().number()).isEqualTo("A1");
        assertThat(response.rows().getFirst().cells().getFirst().status()).isEqualTo("available");
    }

    @Test
    @DisplayName("Should mark locked seats as blocked in response")
    void givenLockedSeats_whenToEventLayoutResponse_thenMarkAsBlocked() {
        EventCategory category = EventCategory.builder().name("Concert").build();
        Event event = Event.builder()
            .id(UUID.randomUUID()).title("Test").organization("Org")
            .PosterUrl("url").status(EventStatus.PUBLISHED)
            .bookingModel(BookingModel.SEAT)
            .startDate(Instant.now()).finishDate(Instant.now().plusSeconds(7200))
            .eventCategory(category).build();

        Section section = Section.builder()
            .id(UUID.randomUUID()).event(event).name("VIP")
            .capacity(10).remainingCapacity(10).build();
        UUID seatId = UUID.randomUUID();
        Seat seat = Seat.builder()
            .id(seatId).section(section).row(0).col(0)
            .lable("A1").status(SeatStatus.AVAILABLE).build();
        section.setSeats(List.of(seat));
        event.setSections(List.of(section));

        EventLayoutResponse response = mapper.toEventLayoutResponse(event, Set.of(seatId));

        assertThat(response.rows().getFirst().cells().getFirst().status()).isEqualTo("blocked");
    }

    @Test
    @DisplayName("Should mark reserved seats in response")
    void givenReservedSeat_whenToEventLayoutResponse_thenMarkAsReserved() {
        EventCategory category = EventCategory.builder().name("Concert").build();
        Event event = Event.builder()
            .id(UUID.randomUUID()).title("Test").organization("Org")
            .PosterUrl("url").status(EventStatus.PUBLISHED)
            .bookingModel(BookingModel.SEAT)
            .startDate(Instant.now()).finishDate(Instant.now().plusSeconds(7200))
            .eventCategory(category).build();

        Section section = Section.builder()
            .id(UUID.randomUUID()).event(event).name("VIP")
            .capacity(10).remainingCapacity(10).build();
        Seat seat = Seat.builder()
            .id(UUID.randomUUID()).section(section).row(0).col(0)
            .lable("A1").status(SeatStatus.BOOKED).build();
        section.setSeats(List.of(seat));
        event.setSections(List.of(section));

        EventLayoutResponse response = mapper.toEventLayoutResponse(event, Set.of());

        assertThat(response.rows().getFirst().cells().getFirst().status()).isEqualTo("reserved");
    }

    @Test
    @DisplayName("Should convert ZONE event without rows")
    void givenZoneEvent_whenToEventLayoutResponse_thenNoRows() {
        EventCategory category = EventCategory.builder().name("Festival").build();
        Event event = Event.builder()
            .id(UUID.randomUUID()).title("Zone Event").organization("Org")
            .PosterUrl("url").status(EventStatus.PUBLISHED)
            .bookingModel(BookingModel.ZONE)
            .startDate(Instant.now()).finishDate(Instant.now().plusSeconds(7200))
            .eventCategory(category).build();
        Section section = Section.builder()
            .id(UUID.randomUUID()).event(event).name("Gold")
            .capacity(100).remainingCapacity(100).build();
        event.setSections(List.of(section));

        EventLayoutResponse response = mapper.toEventLayoutResponse(event, Set.of());

        assertThat(response.mode()).isEqualTo("ZONE_BASED");
        assertThat(response.rows()).isEmpty();
        assertThat(response.categories()).hasSize(1);
        assertThat(response.categories().getFirst().name()).isEqualTo("Gold");
    }

    @Test
    @DisplayName("Should handle null sections gracefully")
    void givenNullSections_whenToEventLayoutResponse_thenReturnEmptyLists() {
        EventCategory category = EventCategory.builder().name("Cat").build();
        Event event = Event.builder()
            .id(UUID.randomUUID()).title("Test").organization("Org")
            .PosterUrl("url").status(EventStatus.PUBLISHED)
            .bookingModel(BookingModel.SEAT)
            .startDate(Instant.now()).finishDate(Instant.now().plusSeconds(7200))
            .eventCategory(category).build();

        EventLayoutResponse response = mapper.toEventLayoutResponse(event, Set.of());

        assertThat(response.rows()).isEmpty();
        assertThat(response.categories()).isEmpty();
    }

    @Test
    @DisplayName("Should convert event with multiple sections and rows sorted by row/col")
    void givenMultiSectionEvent_whenToEventLayoutResponse_thenRowsSorted() {
        EventCategory category = EventCategory.builder().name("Concert").build();
        Event event = Event.builder()
            .id(UUID.randomUUID()).title("Test").organization("Org")
            .PosterUrl("url").status(EventStatus.PUBLISHED)
            .bookingModel(BookingModel.SEAT)
            .startDate(Instant.now()).finishDate(Instant.now().plusSeconds(7200))
            .eventCategory(category).build();

        Section section = Section.builder()
            .id(UUID.randomUUID()).event(event).name("VIP")
            .capacity(4).remainingCapacity(4).build();
        Seat seatA = Seat.builder()
            .id(UUID.randomUUID()).section(section).row(1).col(0)
            .lable("B1").status(SeatStatus.AVAILABLE).build();
        Seat seatB = Seat.builder()
            .id(UUID.randomUUID()).section(section).row(0).col(0)
            .lable("A1").status(SeatStatus.AVAILABLE).build();
        section.setSeats(List.of(seatA, seatB));
        event.setSections(List.of(section));

        EventLayoutResponse response = mapper.toEventLayoutResponse(event, Set.of());

        assertThat(response.rows()).hasSize(2);
        assertThat(response.rows().get(0).label()).isEqualTo("A");
        assertThat(response.rows().get(1).label()).isEqualTo("B");
    }

    @Test
    @DisplayName("Should mark booked seats as BOOKED_ORGANIZER and decrement capacity in buildEvent")
    void givenBookedSeat_whenBuildEvent_thenSetBookedOrganizerAndDecrementCapacity() {
        UUID userId = UUID.randomUUID();
        UUID sectionId = UUID.randomUUID();
        UUID cellId = UUID.randomUUID();

        CreateEventWithLayoutRequest request = new CreateEventWithLayoutRequest(
            "SEAT", "Event", null, UUID.randomUUID(), "Cat",
            "url", Instant.now(), Instant.now().plusSeconds(7200),
            List.of(new CreateEventWithLayoutRequest.RowDto(
                "row-1", "A", false,
                List.of(new CreateEventWithLayoutRequest.CellDto(
                    cellId.toString(), "seat", "A1", "blocked", sectionId.toString()
                ))
            )),
            List.of(new CreateEventWithLayoutRequest.CategoryDto(
                sectionId.toString(), "VIP", "red", 10, BigDecimal.valueOf(100)
            ))
        );

        when(eventCategoryRepository.findByNameIgnoreCase("Cat"))
            .thenReturn(Optional.of(EventCategory.builder().name("Cat").build()));
        when(userServiceClient.getOrganizationName(userId)).thenReturn("Org");

        Event event = mapper.buildEvent(userId, request);

        Section section = event.getSections().getFirst();
        Seat seat = section.getSeats().getFirst();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.BOOKED_ORGANIZER);
        assertThat(section.getRemainingCapacity()).isEqualTo(9);
    }
}
