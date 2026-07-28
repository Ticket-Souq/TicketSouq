package org.ticketsouq.ticketservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.ticketsouq.ticketservice.dto.TicketResponse;
import org.ticketsouq.ticketservice.dto.UpdateTicketStatusRequest;
import org.ticketsouq.ticketservice.service.TicketService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ticket")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @GetMapping
    public ResponseEntity<List<TicketResponse>> getMyTickets(@RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(ticketService.getUserTickets(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketResponse> getTicket(@PathVariable UUID id,
                                                     @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(ticketService.getTicketById(id, userId));
    }

    @GetMapping(params = "reservationId")
    public ResponseEntity<List<TicketResponse>> getByReservation(@RequestParam UUID reservationId) {
        return ResponseEntity.ok(ticketService.getTicketsByReservation(reservationId));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<TicketResponse> updateStatus(@PathVariable UUID id,
                                                       @Valid @RequestBody UpdateTicketStatusRequest request) {
        return ResponseEntity.ok(ticketService.updateTicketStatus(id, request));
    }

    @PostMapping("/{id}/consume")
    public ResponseEntity<TicketResponse> consume(@PathVariable UUID id) {
        return ResponseEntity.ok(ticketService.consumeTicket(id));
    }

    @GetMapping("/organizer/{eventId}")
    public ResponseEntity<List<TicketResponse>> getOrganizerTickets(@PathVariable UUID eventId) {
        return ResponseEntity.ok(ticketService.getOrganizerTickets(eventId));
    }
}
