package org.ticketsouq.ticketservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.ticketsouq.ticketservice.dto.CreateTicketsRequest;
import org.ticketsouq.ticketservice.dto.TicketResponse;
import org.ticketsouq.ticketservice.dto.UpdateTicketStatusRequest;
import org.ticketsouq.ticketservice.service.TicketService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/private/tickets")
@RequiredArgsConstructor
public class TicketPrivateController {

    private final TicketService ticketService;

//    @PostMapping
//    public ResponseEntity<List<TicketResponse>> createTickets(@Valid @RequestBody CreateTicketsRequest request) {
//        return ResponseEntity.status(HttpStatus.CREATED).body(ticketService.createTickets(request));
//    }


}
