package org.ticketsouq.eventservice.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.ticketsouq.eventservice.dto.CreateEventRequest;
import org.ticketsouq.eventservice.dto.EventRequest;
import org.ticketsouq.eventservice.dto.EventResponse;
import org.ticketsouq.eventservice.dto.SeatRequest;
import org.ticketsouq.eventservice.dto.SeatResponse;
import org.ticketsouq.eventservice.dto.SectionRequest;
import org.ticketsouq.eventservice.dto.SectionResponse;
import org.ticketsouq.eventservice.model.enums.EventStatus;
import org.ticketsouq.eventservice.service.EventSearchService;
import org.ticketsouq.eventservice.service.EventService;
import org.ticketsouq.eventservice.service.SeatService;
import org.ticketsouq.eventservice.service.SectionService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
    private final EventSearchService eventSearchService;
    private final SectionService sectionService;
    private final SeatService seatService;

    @PostMapping
    public ResponseEntity<EventResponse> create(@Valid @RequestBody CreateEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.create(request));
    }

    @GetMapping
    public ResponseEntity<Page<EventResponse>> getPublicEvents(Pageable pageable) {
        return ResponseEntity.ok(eventService.getPublicEvents(pageable));
    }

    @GetMapping("/organization")
    public ResponseEntity<Page<EventResponse>> getOrganizationEvents(
            @RequestParam UUID organizationId,
            @RequestParam(required = false) EventStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(eventService.getOrganizationEvents(organizationId, status, pageable));
    }

    @GetMapping("/search/like")
    public ResponseEntity<List<EventResponse>> searchByTitleLike(@RequestParam String title, Pageable pageable) {
        return ResponseEntity.ok(eventSearchService.searchByTitleLike(title, pageable));
    }

    @GetMapping("/search/es")
    public ResponseEntity<List<EventResponse>> searchByTitleEs(@RequestParam String title) {
        return ResponseEntity.ok(eventSearchService.searchByTitleEs(title));
    }

    @GetMapping("/search/es/fuzzy")
    public ResponseEntity<List<EventResponse>> searchByTitleFuzzy(@RequestParam String title) {
        return ResponseEntity.ok(eventSearchService.searchByTitleFuzzy(title));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(eventService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EventResponse> update(@PathVariable UUID id, @Valid @RequestBody EventRequest request) {
        return ResponseEntity.ok(eventService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable UUID id) {
        eventService.cancel(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<EventResponse> updateStatus(@PathVariable UUID id, @RequestParam EventStatus status) {
        return ResponseEntity.ok(eventService.updateStatus(id, status));
    }

    @PostMapping("/{eventId}/sections")
    public ResponseEntity<SectionResponse> createSection(@PathVariable UUID eventId, @Valid @RequestBody SectionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sectionService.create(eventId, request));
    }

    @GetMapping("/{eventId}/sections")
    public ResponseEntity<List<SectionResponse>> getSections(@PathVariable UUID eventId) {
        return ResponseEntity.ok(sectionService.getByEventId(eventId));
    }

    @GetMapping("/sections/{sectionId}")
    public ResponseEntity<SectionResponse> getSectionById(@PathVariable UUID sectionId) {
        return ResponseEntity.ok(sectionService.getById(sectionId));
    }

    @PutMapping("/sections/{sectionId}")
    public ResponseEntity<SectionResponse> updateSection(@PathVariable UUID sectionId, @Valid @RequestBody SectionRequest request) {
        return ResponseEntity.ok(sectionService.update(sectionId, request));
    }

    @DeleteMapping("/sections/{sectionId}")
    public ResponseEntity<Void> deleteSection(@PathVariable UUID sectionId) {
        sectionService.delete(sectionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sections/{sectionId}/seats")
    public ResponseEntity<SeatResponse> createSeat(@PathVariable UUID sectionId, @Valid @RequestBody SeatRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(seatService.create(sectionId, request));
    }

    @GetMapping("/sections/{sectionId}/seats")
    public ResponseEntity<List<SeatResponse>> getSeats(@PathVariable UUID sectionId) {
        return ResponseEntity.ok(seatService.getBySectionId(sectionId));
    }

    @GetMapping("/seats/{seatId}")
    public ResponseEntity<SeatResponse> getSeatById(@PathVariable UUID seatId) {
        return ResponseEntity.ok(seatService.getById(seatId));
    }

    @PutMapping("/seats/{seatId}")
    public ResponseEntity<SeatResponse> updateSeat(@PathVariable UUID seatId, @Valid @RequestBody SeatRequest request) {
        return ResponseEntity.ok(seatService.update(seatId, request));
    }

    @DeleteMapping("/seats/{seatId}")
    public ResponseEntity<Void> deleteSeat(@PathVariable UUID seatId) {
        seatService.delete(seatId);
        return ResponseEntity.noContent().build();
    }
}
