package org.ticketsouq.eventservice.Mapper;

import org.springframework.stereotype.Component;
import org.ticketsouq.eventservice.dto.CreateEventWithLayoutRequest;
import org.ticketsouq.eventservice.model.Event;
import org.ticketsouq.eventservice.model.Seat;
import org.ticketsouq.eventservice.model.Section;
import org.ticketsouq.eventservice.model.enums.SeatStatus;

import java.util.*;

@Component
public class EventFrontendMapper {

    public EventCreationBundle map(CreateEventWithLayoutRequest request) {
        Event event = buildEvent(request);

        List<Section> sections = buildSections(request, event);
        Map<String, Section> categorySectionMap = indexByCategoryId(request, sections);

        List<Seat> seats = buildSeats(request, categorySectionMap);

        event.setSections(sections);

        return new EventCreationBundle(event, sections, seats);
    }

    private Event buildEvent(CreateEventWithLayoutRequest request) {
        return Event.builder()
                .title(request.title())
                .description(request.description())
                .venueId(request.venueId())
                .organizationId(request.organizationId())
                .createdBy(request.createdBy())
                .PosterUrl(request.posterUrl())
                .status(request.status())
                .bookingModel(request.bookingModel())
                .startDate(request.startDate())
                .finishDate(request.finishDate())
                .build();
    }

    private List<Section> buildSections(CreateEventWithLayoutRequest request, Event event) {
        List<Section> sections = new ArrayList<>();
        if (request.categories() == null) return sections;

        for (EventFrontendDto.CategoryDto cat : request.categories()) {
            Section section = Section.builder()
                    .event(event)
                    .name(cat.name())
                    .capacity(cat.capacity())
                    .remainingCapacity(cat.capacity())
                    .color(cat.color())
                    .price(cat.price())
                    .build();
            sections.add(section);
        }
        return sections;
    }

    private Map<String, Section> indexByCategoryId(CreateEventWithLayoutRequest request, List<Section> sections) {
        Map<String, Section> map = new LinkedHashMap<>();
        if (request.categories() == null) return map;

        for (int i = 0; i < request.categories().size(); i++) {
            String catId = request.categories().get(i).id();
            map.put(catId, sections.get(i));
        }
        return map;
    }

    private List<Seat> buildSeats(CreateEventWithLayoutRequest request, Map<String, Section> categorySectionMap) {
        List<Seat> seats = new ArrayList<>();
        if (request.rows() == null) return seats;

        for (int rowIdx = 0; rowIdx < request.rows().size(); rowIdx++) {
            EventFrontendDto.RowDto rowDto = request.rows().get(rowIdx);
            if (rowDto.cells() == null) continue;

            for (int colIdx = 0; colIdx < rowDto.cells().size(); colIdx++) {
                EventFrontendDto.CellDto cell = rowDto.cells().get(colIdx);

                Section section = categorySectionMap.get(cell.categoryId());
                if (section == null) continue;

                int col = parseCol(cell.number(), colIdx);
                String lable = rowDto.label() + cell.number();

                Seat seat = Seat.builder()
                        .section(section)
                        .row(rowIdx)
                        .col(col)
                        .lable(lable)
                        .status(mapStatus(cell.status()))
                        .price(null)
                        .build();
                seats.add(seat);
            }
        }
        return seats;
    }

    private int parseCol(String number, int fallback) {
        if (number == null) return fallback;
        try {
            return Integer.parseInt(number);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private SeatStatus mapStatus(String frontendStatus) {
        if (frontendStatus == null) return SeatStatus.AVAILABLE;
        return switch (frontendStatus.toLowerCase()) {
            case "available" -> SeatStatus.AVAILABLE;
            case "blocked" -> SeatStatus.LOCKED;
            case "reserved" -> SeatStatus.BOOKED;
            default -> SeatStatus.AVAILABLE;
        };
    }

    public record EventCreationBundle(
            Event event,
            List<Section> sections,
            List<Seat> seats
    ) {}
}
