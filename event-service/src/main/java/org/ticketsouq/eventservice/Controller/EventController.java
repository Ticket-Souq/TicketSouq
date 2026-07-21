package org.ticketsouq.eventservice.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.ticketsouq.eventservice.dto.*;
import org.ticketsouq.eventservice.dto.FrontendMap.CreateEventWithLayoutRequest;
import org.ticketsouq.eventservice.dto.FrontendMap.EventCardResponse;
import org.ticketsouq.eventservice.dto.FrontendMap.EventLayoutResponse;
import org.ticketsouq.eventservice.service.EventService;
import org.ticketsouq.eventservice.service.LockService;
import org.ticketsouq.eventservice.service.Search.SearchService;
import org.ticketsouq.eventservice.service.SeatService;
import org.ticketsouq.eventservice.service.SectionService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {
    private final EventService eventService;
    private final SearchService eventSearchService;
    private final SectionService sectionService;
    private final SeatService seatService;
    private final LockService lockService;


    @PostMapping("/{eventId}/sections")
    public ResponseEntity<SectionResponse> createSection(@PathVariable UUID eventId, @Valid @RequestBody CreateSectionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sectionService.createSection(eventId, request));
    }

    @PostMapping
    public ResponseEntity<Void> create(@RequestHeader("X-User-Id") UUID userId, @RequestBody CreateEventWithLayoutRequest request) {
        eventService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping
    public ResponseEntity<Page<EventCardResponse>> getEvents(@RequestHeader("X-User-Id") UUID userId, Pageable pageable) {
        return ResponseEntity.ok(eventService.getEvents(userId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventLayoutResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(eventService.getById(id));
    }

    @PatchMapping("/sections/{sectionId}")
    public ResponseEntity<SectionResponse> updateSection(@PathVariable UUID sectionId, @Valid @RequestBody UpdateSectionRequest request, @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(sectionService.updateSection(sectionId, request, userId));
    }

    @DeleteMapping("/{eventId}") // must be org_head / admin
    public ResponseEntity<Void> cancelEvent(@PathVariable UUID eventId, @RequestHeader("X-User-Id") UUID userId) {
        eventService.cancelEvent(eventId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public ResponseEntity<Page<EventCardResponse>> searchBy(@ModelAttribute EventSearchRequest request, Pageable pageable) {
        return ResponseEntity.ok(eventSearchService.searchBy(request, pageable));
    }

    @PatchMapping("/seats/{seatId}/status") // must be org_head / agent
    public ResponseEntity<SeatResponse> updateOrganizerSeatStatus(@PathVariable UUID seatId, @Valid @RequestBody UpdateSeatStatusRequest request, @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(seatService.updateOrganizerSeatStatus(seatId, request, userId));
    }

    @GetMapping("/{eventId}/zones")
    public ResponseEntity<List<ZoneStatusResponse>> getZoneStatuses(@PathVariable UUID eventId) {
        return ResponseEntity.ok(lockService.getZoneStatuses(eventId));
    }
}
