package org.ticketsouq.eventservice.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.ticketsouq.eventservice.dto.SeatResponse;
import org.ticketsouq.eventservice.dto.UpdateSeatStatusRequest;
import org.ticketsouq.eventservice.model.Event;
import org.ticketsouq.eventservice.model.Seat;
import org.ticketsouq.eventservice.model.Section;
import org.ticketsouq.eventservice.model.enums.BookingModel;
import org.ticketsouq.eventservice.model.enums.EventStatus;
import org.ticketsouq.eventservice.model.enums.SeatStatus;
import org.ticketsouq.eventservice.repository.SeatRepository;
import org.ticketsouq.sharedmodule.GeneralExceptions.BadRequestException;
import org.ticketsouq.sharedmodule.GeneralExceptions.ConflictException;
import org.ticketsouq.sharedmodule.GeneralExceptions.ForbiddenException;
import org.ticketsouq.sharedmodule.GeneralExceptions.ResourceNotFoundException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SeatService {
    private final SeatRepository seatRepository;
    private final AuthorizationService authorizationService;

    @Transactional
    public SeatResponse updateOrganizerSeatStatus(
        UUID seatId,
        UpdateSeatStatusRequest request,
        UUID userId
    ) {

        Seat seat = seatRepository.findById(seatId)
            .orElseThrow(() ->
                new ResourceNotFoundException("Seat not found.", seatId));

        Event event = seat.getSection().getEvent();

        if (!authorizationService.validateOrganizer(event.getOrganizationId(), userId)) {
            throw new ForbiddenException(
                "User is not allowed to modify this event."
            );

        }

        validateEventCanBeUpdated(event);

        validateSeatBased(event);

        validateSeatStatusTransition(
            seat.getStatus(),
            request.status()
        );

        updateRemainingCapacity(
            seat.getSection(),
            seat.getStatus(),
            request.status()
        );

        seat.setStatus(request.status());

        return SeatResponse.from(
            seatRepository.save(seat)
        );
    }
    private void validateSeatBased(Event event) {

        if (event.getBookingModel() != BookingModel.SEAT) {
            throw new BadRequestException(
                "This endpoint is only available for seat-based events."
            );
        }
    }
    private void validateSeatStatusTransition(
        SeatStatus current,
        SeatStatus target
    ) {

        if (current == SeatStatus.AVAILABLE
            && target == SeatStatus.BOOKED_ORGANIZER) {
            return;
        }

        if (current == SeatStatus.BOOKED_ORGANIZER
            && target == SeatStatus.AVAILABLE) {
            return;
        }

        throw new BadRequestException(
            "Invalid seat status transition."
        );
    }
    private void updateRemainingCapacity(
        Section section,
        SeatStatus current,
        SeatStatus target
    ) {

        if (current == SeatStatus.AVAILABLE
            && target == SeatStatus.BOOKED_ORGANIZER) {

            section.setRemainingCapacity(
                section.getRemainingCapacity() - 1
            );

            return;
        }

        if (current == SeatStatus.BOOKED_ORGANIZER
            && target == SeatStatus.AVAILABLE) {

            section.setRemainingCapacity(
                section.getRemainingCapacity() + 1
            );
        }
    }
    private void validateEventCanBeUpdated(Event event) {

        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new ConflictException(
                "Only published events can be updated."
            );
        }
    }
}
