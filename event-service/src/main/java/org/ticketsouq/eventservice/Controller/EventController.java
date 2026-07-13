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
        //(ahmed)
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.create(request));
    }

    @GetMapping
    public ResponseEntity<Page<EventResponse>> getPublicEvents(Pageable pageable) {
        // get events that are active or published for user (omar)
        return ResponseEntity.ok(eventService.getPublicEvents(pageable));
    }

    @GetMapping("/organization")
    public ResponseEntity<Page<EventResponse>> getOrganizationEvents(@RequestParam UUID organizationId, @RequestParam(required = false) EventStatus status, Pageable pageable) {
        // get all events belong to organizer (all status) (ahmed)
        return ResponseEntity.ok(eventService.getOrganizationEvents(organizationId, status, pageable));
    }

    @GetMapping("/search/like")
    public ResponseEntity<List<EventResponse>> searchByTitlePS(@RequestParam String title, Pageable pageable) {
        // search by postgres  (Omar)
        return ResponseEntity.ok(eventSearchService.searchByTitlePS(title, pageable));
    }

    @GetMapping("/search/es/fuzzy")
    public ResponseEntity<List<EventResponse>> searchByTitleFuzzy(@RequestParam String title) {
        // search by ES (Ahmed)
        return ResponseEntity.ok(eventSearchService.searchByTitleFuzzy(title));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> getById(@PathVariable UUID id) {
        // (ahmed)
        return ResponseEntity.ok(eventService.getById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable UUID id) {
        // (omar) update state and send event.cancel event for reservation service to activate refund flow
        eventService.cancel(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<EventResponse> updateStatus(@PathVariable UUID id, @RequestParam EventStatus status) {
        // (ahmed) not endpoint --> job
        return ResponseEntity.ok(eventService.updateStatus(id, status));
    }

    @PutMapping("/sections/{sectionId}")
    public ResponseEntity<SectionResponse> updateSection(@PathVariable UUID sectionId, @Valid @RequestBody SectionRequest request) {
        //(omar)
        return ResponseEntity.ok(sectionService.update(sectionId, request));
    }

    @PutMapping("/seats/{seatId}")
    public ResponseEntity<SeatResponse> updateSeatStatusCustomer(@PathVariable UUID seatId, @Valid @RequestBody SeatRequest request) {
        // TODO change endpoint name
        // customer (ahmed)
        return ResponseEntity.ok(seatService.update(seatId, request));
    }

    @PutMapping("/seats/{seatId}")
    public ResponseEntity<SeatResponse> updateSeatStatusOrganizer(@PathVariable UUID seatId, @Valid @RequestBody SeatRequest request) {
        // TODO change endpoint name
        // organizer can change status of seat direct without any other service involved to reserve or to (omar)
        return ResponseEntity.ok(seatService.update(seatId, request));
    }
}
