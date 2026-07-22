package org.ticketsouq.ticketservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.ticketservice.dto.CreateTicketsRequest;
import org.ticketsouq.ticketservice.dto.TicketResponse;
import org.ticketsouq.ticketservice.dto.UpdateTicketStatusRequest;
import org.ticketsouq.ticketservice.models.SeatTicket;
import org.ticketsouq.ticketservice.models.Ticket;
import org.ticketsouq.ticketservice.models.ZoneTicket;
import org.ticketsouq.ticketservice.repository.TicketRepository;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;

    @Transactional
    public List<TicketResponse> createTickets(CreateTicketsRequest request) {
        return request.tickets().stream().map(item -> {
            Ticket ticket;
            if ("SEAT".equals(item.type())) {
                SeatTicket st = new SeatTicket();
                st.setSeatId(item.seatId());
                st.setRow(item.row());
                st.setSeatNumber(item.seatNumber());
                st.setCategory(item.category());
                ticket = st;
            } else {
                ZoneTicket zt = new ZoneTicket();
                zt.setSectionId(item.sectionId());
                zt.setCategory(item.category());
                ticket = zt;
            }

            ticket.setReservationId(request.reservationId());
            ticket.setUserId(request.userId());
            ticket.setEventId(request.eventId());
            ticket.setEventTitle(request.eventTitle());
            ticket.setEventStartDate(request.eventStartDate());
            ticket.setEventFinishDate(request.eventFinishDate());
            ticket.setEventPosterUrl(request.eventPosterUrl());
            ticket.setEventStatus(request.eventStatus());
            ticket.setPrice(item.price());
            ticket.setReservationStatus("ACTIVE");
            ticket.setConsumed(false);

            ticketRepository.save(ticket);
            return toResponse(ticket);
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> getUserTickets(UUID userId) {
        return ticketRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TicketResponse getTicketById(UUID ticketId, UUID userId) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new EntityNotFoundException("Ticket not found: " + ticketId));
        if (!ticket.getUserId().equals(userId)) {
            throw new SecurityException("Ticket does not belong to user");
        }
        return toResponse(ticket);
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> getTicketsByReservation(UUID reservationId) {
        return ticketRepository.findByReservationId(reservationId)
            .stream().map(this::toResponse).toList();
    }

    @Transactional
    public TicketResponse updateTicketStatus(UUID ticketId, UpdateTicketStatusRequest request) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new EntityNotFoundException("Ticket not found: " + ticketId));
        ticket.setReservationStatus(request.reservationStatus());
        ticketRepository.save(ticket);
        return toResponse(ticket);
    }

    @Transactional
    public TicketResponse consumeTicket(UUID ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new EntityNotFoundException("Ticket not found: " + ticketId));
        if (ticket.isConsumed()) {
            throw new IllegalStateException("Ticket already consumed");
        }
        ticket.setConsumed(true);
        ticketRepository.save(ticket);
        return toResponse(ticket);
    }

    private TicketResponse toResponse(Ticket ticket) {
        TicketResponse.TicketResponseBuilder builder = TicketResponse.builder()
            .id(ticket.getId())
            .ticketType(ticket instanceof SeatTicket ? "SEAT" : "ZONE")
            .eventTitle(ticket.getEventTitle())
            .eventStartDate(ticket.getEventStartDate())
            .eventFinishDate(ticket.getEventFinishDate())
            .eventPosterUrl(ticket.getEventPosterUrl())
            .eventStatus(ticket.getEventStatus())
            .price(ticket.getPrice())
            .reservationStatus(ticket.getReservationStatus())
            .consumed(ticket.isConsumed())
            .createdAt(ticket.getCreatedAt());

        if (ticket instanceof SeatTicket st) {
            builder.row(st.getRow())
                .seatNumber(st.getSeatNumber())
                .seatCategory(st.getCategory());
        } else if (ticket instanceof ZoneTicket zt) {
            builder.zoneCategory(zt.getCategory());
        }

        return builder.build();
    }
}
