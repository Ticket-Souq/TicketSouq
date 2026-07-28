package org.ticketsouq.eventservice.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.ticketsouq.eventservice.dto.*;
import org.ticketsouq.eventservice.model.EventCategory;
import org.ticketsouq.eventservice.repository.EventCategoryRepository;
import org.ticketsouq.eventservice.service.EventService;
import org.ticketsouq.eventservice.service.LockService;
import org.ticketsouq.eventservice.service.Search.SearchService;
import org.ticketsouq.eventservice.service.SeatService;
import org.ticketsouq.eventservice.service.SectionService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/event")
@RequiredArgsConstructor
public class EventController {
    private final EventService eventService;
    private final EventCategoryRepository eventCategoryRepository;
    private final SearchService SearchProvider;
    private final SectionService sectionService;
    private final SeatService seatService;
    private final LockService lockService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> create(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestPart("poster") MultipartFile poster,
            @RequestPart("event") @Valid CreateEventRequest request) {
        eventService.create(userId, request, poster);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/categories")
    public ResponseEntity<List<String>> getCategories() {
        List<String> categories = eventCategoryRepository.findAll().stream()
            .map(EventCategory::getName)
            .sorted()
            .toList();
        return ResponseEntity.ok(categories);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventFullResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(eventService.getById(id));
    }

    @PostMapping("/{eventId}/sections")
    public ResponseEntity<SectionResponse> createSection(@PathVariable UUID eventId, @Valid @RequestBody CreateSectionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sectionService.createSection(eventId, request));
    }
    @GetMapping
    public ResponseEntity<Page<EventCardResponse>> getEvents(@RequestHeader("X-User-Id") UUID userId, Pageable pageable) {
        return ResponseEntity.ok(eventService.getEvents(userId, pageable));
    }

    @GetMapping("/management")
    public ResponseEntity<Page<EventFullResponse>> getManagementEvents(@RequestHeader("X-User-Id") UUID userId, Pageable pageable) {
        return ResponseEntity.ok(eventService.getManagementEvents(userId, pageable));
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
    public ResponseEntity<Page<EventCardResponse>> searchBy(@RequestHeader("X-User-Id") UUID userId, @ModelAttribute EventSearchRequest request, Pageable pageable) {
        if (request.title() == null && request.organization() == null && request.category() == null) {
            return ResponseEntity.ok(eventService.getEvents(userId, pageable));
        }else{
            return ResponseEntity.ok(SearchProvider.searchBy(request, pageable));
        }
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
