package org.ticketsouq.eventservice.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.ticketsouq.eventservice.Client.UserServiceClient;
import org.ticketsouq.eventservice.dto.CreateEventRequest;
import org.ticketsouq.eventservice.dto.EventFullResponse;
import org.ticketsouq.eventservice.model.Event;
import org.ticketsouq.eventservice.model.EventCategory;
import org.ticketsouq.eventservice.model.Seat;
import org.ticketsouq.eventservice.model.Section;
import org.ticketsouq.eventservice.model.enums.BookingModel;
import org.ticketsouq.eventservice.model.enums.EventStatus;
import org.ticketsouq.eventservice.model.enums.SeatStatus;
import org.ticketsouq.eventservice.repository.EventCategoryRepository;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class EventMapper {

    private final EventCategoryRepository eventCategoryRepository;
    private final UserServiceClient userServiceClient;

    public Event buildEvent(UUID userId, CreateEventRequest request, String posterUrl) {
        EventCategory category = resolveCategory(request.eventCategoryName());
        String organization = userServiceClient.getOrganizationName(userId);
        BookingModel bookingModel = request.bookingModel() != null ? request.bookingModel() : BookingModel.SEAT;

        Event event = Event.builder()
            .title(request.title())
            .description(request.description())
            .location(request.location())
            .venueTemplateId(request.venueTemplateId())
            .eventCategory(category)
            .organization(organization)
            .createdBy(userId)
            .PosterUrl(posterUrl)
            .status(EventStatus.PUBLISHED)
            .bookingModel(bookingModel)
            .startDate(request.startDate())
            .finishDate(request.finishDate())
            .build();

        if (request.sections() != null) {
            List<Section> sections = request.sections().stream()
                .map(sectionDto -> buildSection(event, sectionDto, bookingModel))
                .toList();
            event.setSections(sections);
        }

        return event;
    }

    public EventFullResponse toEventFullResponse(Event event, Set<UUID> lockedSeatIds) {
        List<EventFullResponse.SectionFullResponse> sections = Optional.ofNullable(event.getSections())
            .orElse(List.of())
            .stream()
            .map(section -> toSectionFullResponse(section, lockedSeatIds))
            .toList();

        return new EventFullResponse(
            event.getId(),
            event.getTitle(),
            event.getDescription(),
            event.getLocation(),
            event.getVenueTemplateId(),
            event.getEventCategory() != null ? event.getEventCategory().getName() : null,
            event.getOrganization(),
            event.getPosterUrl(),
            event.getStatus(),
            event.getBookingModel(),
            event.getStartDate(),
            event.getFinishDate(),
            sections
        );
    }

    private Section buildSection(Event event, CreateEventRequest.CreateSectionRequest dto, BookingModel bookingModel) {
        Section section = Section.builder()
            .templateSectionId(dto.id())
            .event(event)
            .name(dto.name())
            .capacity(dto.capacity())
            .remainingCapacity(dto.capacity())
            .color(dto.color())
            .price(dto.price())
            .build();

        if (bookingModel == BookingModel.SEAT && dto.seats() != null) {
            Set<Seat> seats = dto.seats().stream()
                .map(seatDto -> buildSeat(section, seatDto))
                .collect(Collectors.toSet());
            section.setSeats(seats);
            section.setRemainingCapacity(dto.capacity() - (int) seats.stream()
                .filter(s -> s.getStatus() != SeatStatus.AVAILABLE)
                .count());
        } else {
            section.setSeats(new LinkedHashSet<>());
        }

        return section;
    }

    private Seat buildSeat(Section section, CreateEventRequest.CreateSectionRequest.CreateSeatRequest dto) {
        SeatStatus status = mapSeatStatus(dto.status());
        return Seat.builder()
            .templateSeatId(dto.id())
            .section(section)
            .lable(dto.lable())
            .status(status)
            .build();
    }

    private EventCategory resolveCategory(String categoryName) {
        if (categoryName == null) return null;
        return eventCategoryRepository.findByNameIgnoreCase(categoryName)
            .orElseGet(() -> eventCategoryRepository.save(
                EventCategory.builder().name(categoryName).build()));
    }

    private SeatStatus mapSeatStatus(SeatStatus status) {
        if (status == null) return SeatStatus.AVAILABLE;
        return switch (status) {
            case BOOKED -> SeatStatus.BOOKED_ORGANIZER;
            default -> status;
        };
    }

    private EventFullResponse.SectionFullResponse toSectionFullResponse(Section section, Set<UUID> lockedSeatIds) {
        List<EventFullResponse.SectionFullResponse.SeatFullResponse> seats = Optional.ofNullable(section.getSeats())
            .orElse(Set.of())
            .stream()
            .map(seat -> toSeatFullResponse(seat, lockedSeatIds))
            .toList();

        return new EventFullResponse.SectionFullResponse(
            section.getId(),
            section.getTemplateSectionId(),
            section.getName(),
            section.getCapacity(),
            section.getRemainingCapacity(),
            section.getColor(),
            section.getPrice(),
            seats
        );
    }

    private EventFullResponse.SectionFullResponse.SeatFullResponse toSeatFullResponse(Seat seat, Set<UUID> lockedSeatIds) {
        SeatStatus status;
        if (lockedSeatIds.contains(seat.getId())) {
            status = SeatStatus.BOOKED;
        } else {
            status = seat.getStatus();
        }
        return new EventFullResponse.SectionFullResponse.SeatFullResponse(
            seat.getId(),
            seat.getTemplateSeatId(),
            status
        );
    }
}
