package org.ticketsouq.ticketservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.ticketsouq.ticketservice.dto.TicketResponse;
import org.ticketsouq.ticketservice.service.TicketService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
@CrossOrigin(allowCredentials = "*")
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
}
