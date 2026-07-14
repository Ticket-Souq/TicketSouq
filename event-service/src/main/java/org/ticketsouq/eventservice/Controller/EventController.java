package org.ticketsouq.eventservice.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.ticketsouq.eventservice.dto.*;
import org.ticketsouq.eventservice.service.EventService;
import org.ticketsouq.eventservice.service.SeatService;
import org.ticketsouq.eventservice.service.SectionService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {
    private final EventService eventService;
    private final SectionService sectionService;
    private final SeatService seatService;

    @GetMapping
    public ResponseEntity<Page<EventCardResponse>> getPublicEvents(
        @PageableDefault(
            sort = "startDate",
            direction = Sort.Direction.ASC
        ) Pageable pageable) {

        return ResponseEntity.ok(eventService.getPublicEvents(pageable));
    }

    @PatchMapping("/sections/{sectionId}")
    public ResponseEntity<SectionResponse> updateSection(
        @PathVariable UUID sectionId,
        @Valid @RequestBody UpdateSectionRequest request,
        @RequestHeader("X-User-Id") UUID userId
    ) {

        return ResponseEntity.ok(
            sectionService.updateSection(
                sectionId,
                request,
                userId
            )
        );
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<Void> cancelEvent(
        @PathVariable UUID eventId,
        @RequestHeader("X-User-Id") UUID userId
    ) {

        eventService.cancelEvent(eventId, userId);

        return ResponseEntity.noContent().build();
    }
    @PatchMapping("/seats/{seatId}/status")
    public ResponseEntity<SeatResponse> updateOrganizerSeatStatus(
        @PathVariable UUID seatId,
        @Valid @RequestBody UpdateSeatStatusRequest request,
        @RequestHeader("X-User-Id") UUID userId
    ) {

        return ResponseEntity.ok(
            seatService.updateOrganizerSeatStatus(
                seatId,
                request,
                userId
            )
        );
    }

}
