package org.ticketsouq.eventservice.dto.FrontendMap;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.ticketsouq.eventservice.Client.UserServiceClient;
import org.ticketsouq.eventservice.model.Event;
import org.ticketsouq.eventservice.model.EventCategory;
import org.ticketsouq.eventservice.model.Seat;
import org.ticketsouq.eventservice.model.Section;
import org.ticketsouq.eventservice.model.enums.BookingModel;
import org.ticketsouq.eventservice.model.enums.EventStatus;
import org.ticketsouq.eventservice.model.enums.SeatStatus;
import org.ticketsouq.eventservice.repository.EventCategoriesRepository;
import org.ticketsouq.sharedmodule.utils.UUIDUtils;

import java.util.*;

@Component
@RequiredArgsConstructor
public class EventFrontendMapper {

    private final EventCategoriesRepository eventCategoriesRepository;
    private final UserServiceClient userServiceClient;

    /// DTO to Entity
    public Event buildEvent(UUID createdBy, CreateEventWithLayoutRequest request) {
        // TODO save each section and each seat
        Event event = Event.builder()
                .title(request.name())
                .description(request.description())
                .venueTemplateId(request.id())
                .eventCategory(
                    eventCategoriesRepository.findByName(request.categoryName()).orElse(
                        eventCategoriesRepository.save(EventCategory.builder().name(request.categoryName()).build())
                    )
                )
                // TODO make mock in user service
                .organization(userServiceClient.getOrganizationName(createdBy))
                .createdBy(createdBy)
                .PosterUrl(request.posterUrl())
                .status(EventStatus.PUBLISHED)
                .bookingModel(mapBookingModel(request.mode()))
                .startDate(request.startDate())
                .finishDate(request.finishDate())
                .build();

        Map<UUID, Section> categorySectionMap = buildSections(request.categories(), event);
        if (event.getBookingModel() == BookingModel.SEAT) {
            buildSeats(request.rows(), categorySectionMap);
        }

        event.setSections(new ArrayList<>(categorySectionMap.values()));
        return event;
    }
    private Map<UUID, Section> buildSections(List<CreateEventWithLayoutRequest.CategoryDto> categories, Event event) {
        Map<UUID, Section> map = new LinkedHashMap<>();

        if (categories == null) return map;

        for (CreateEventWithLayoutRequest.CategoryDto cat : categories) {
            Section section = Section.builder()
                    .id(UUIDUtils.parse(cat.id()))
                    .event(event)
                    .name(cat.name())
                    .capacity(cat.capacity())
                    .remainingCapacity(cat.capacity())
                    .color(cat.color())
                    .price(cat.price())
                    .build();
            map.put(section.getId(), section);

        }
        return map;
    }
    private void buildSeats(List<CreateEventWithLayoutRequest.RowDto> rows, Map<UUID, Section> categorySectionMap) {
        if (rows == null) return;

        for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
            CreateEventWithLayoutRequest.RowDto rowDto = rows.get(rowIdx);
            if (rowDto.cells() == null) continue;

            for (int colIdx = 0; colIdx < rowDto.cells().size(); colIdx++) {
                CreateEventWithLayoutRequest.CellDto cell = rowDto.cells().get(colIdx);

                Section section = categorySectionMap.get(UUIDUtils.parse(cell.categoryId()));

                if (section == null) continue;

                Seat seat = Seat.builder()
                        .id(UUIDUtils.parse(cell.id()))
                        .section(section)
                        .row(rowIdx)
                        .col(colIdx)
                        .lable(cell.number())
                        .status(mapSeatStatus(cell.status()))
                        .build();
                section.getSeats().add(seat);

                if (seat.getStatus().equals(SeatStatus.BOOKED)) {
                    seat.setStatus(SeatStatus.BOOKED_ORGANIZER);
                    section.setRemainingCapacity(section.getRemainingCapacity()-1);
                }

            }
        }
    }
    private BookingModel mapBookingModel(String mode) {
        if (mode == null) return BookingModel.SEAT;
        return switch (mode.toUpperCase()) {
            case "ZONE", "ZONE_BASED" -> BookingModel.ZONE;
            case "MIXED" -> BookingModel.MIXED;
            default -> BookingModel.SEAT;
        };
    }
    private SeatStatus mapSeatStatus(String frontendStatus) {
        if (frontendStatus == null) return SeatStatus.AVAILABLE;
        return switch (frontendStatus.toLowerCase()) {
            case "blocked" -> SeatStatus.LOCKED;
            case "reserved" -> SeatStatus.BOOKED;
            default -> SeatStatus.AVAILABLE;
        };
    }

    /// Entity to DTO
    public EventLayoutResponse toEventLayoutResponse(Event event) {
        List<Section> sections = event.getSections() != null ? event.getSections() : List.of();

        List<EventLayoutResponse.CategoryResponse> categories = sections.stream()
            .map(this::toCategoryResponse)
            .toList();

        List<EventLayoutResponse.RowResponse> rows = event.getBookingModel() == BookingModel.SEAT ? buildRows(sections) : List.of();

        return new EventLayoutResponse(
            event.getId(),
            toFrontendMode(event.getBookingModel()),
            event.getTitle(),
            event.getDescription(),
            event.getVenueTemplateId(),
            event.getOrganization(),
            event.getStatus().name(),
            event.getEventCategory().getName(),
            event.getPosterUrl(),
            event.getStartDate(),
            event.getFinishDate(),
            rows,
            categories
        );
    }
    private EventLayoutResponse.CategoryResponse toCategoryResponse(Section section) {
        return new EventLayoutResponse.CategoryResponse(
            section.getId(),
            section.getName(),
            section.getColor(),
            section.getCapacity(),
            section.getRemainingCapacity(),
            section.getPrice()
        );
    }
    private List<EventLayoutResponse.RowResponse> buildRows(List<Section> sections) {
        Map<Integer, List<Seat>> seatsByRow = new TreeMap<>();

        for (Section section : sections) {
            if (section.getSeats() == null) continue;
            for (Seat seat : section.getSeats()) {
                seatsByRow.computeIfAbsent(seat.getRow(), k -> new ArrayList<>()).add(seat);
            }
        }

        List<EventLayoutResponse.RowResponse> rows = new ArrayList<>();
        for (Map.Entry<Integer, List<Seat>> entry : seatsByRow.entrySet()) {
            int rowIdx = entry.getKey();
            List<Seat> seatsInRow = entry.getValue();
            seatsInRow.sort(Comparator.comparingInt(Seat::getCol));

            List<EventLayoutResponse.CellResponse> cells = seatsInRow.stream()
                .map(this::toCellResponse)
                .toList();

            rows.add(new EventLayoutResponse.RowResponse(
                toRowLabel(rowIdx),
                false,
                cells
            ));
        }

        return rows;
    }
    private String toRowLabel(int rowIndex) {
        StringBuilder sb = new StringBuilder();
        int n = rowIndex;
        do {
            sb.append((char) ('A' + (n % 26)));
            n = n / 26 - 1;
        } while (n >= 0);
        return sb.reverse().toString();
    }
    private EventLayoutResponse.CellResponse toCellResponse(Seat seat) {
        return new EventLayoutResponse.CellResponse(
            seat.getId(),
            "seat",
            seat.getLable(),
            toFrontendStatus(seat.getStatus()),
            seat.getSection().getId()
        );
    }
    private String toFrontendStatus(SeatStatus status) {
        if (status == null) return "available";
        return switch (status) {
            case AVAILABLE -> "available";
            case LOCKED -> "blocked";
            case BOOKED, BOOKED_ORGANIZER -> "reserved";
        };
    }
    private String toFrontendMode(BookingModel model) {
        if (model == null) return "SEAT_BASED";
        return switch (model) {
            case SEAT -> "SEAT_BASED";
            case ZONE -> "ZONE_BASED";
            case MIXED -> "MIXED";
        };
    }

}
