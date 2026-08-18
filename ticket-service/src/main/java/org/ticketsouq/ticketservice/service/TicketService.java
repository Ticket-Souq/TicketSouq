package org.ticketsouq.ticketservice.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.sharedmodule.TicketService.dto.CreateTicketRequest;
import org.ticketsouq.ticketservice.dto.CreateTicketsRequest;
import org.ticketsouq.ticketservice.dto.OrganizerReserveRequest;
import org.ticketsouq.ticketservice.dto.UpdateTicketStatusRequest;
import org.ticketsouq.ticketservice.dto.TicketResponse;
import org.ticketsouq.ticketservice.model.EventSnapshot;
import org.ticketsouq.ticketservice.models.SeatTicket;
import org.ticketsouq.ticketservice.models.Ticket;
import org.ticketsouq.ticketservice.models.ZoneTicket;
import org.ticketsouq.ticketservice.repository.TicketRepository;
import org.ticketsouq.sharedmodule.TicketService.dto.CancelTicketRequest;
import org.ticketsouq.sharedmodule.EventService.dto.TicketReservationDto;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.ticketsouq.ticketservice.metrics.TicketMetrics;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final EventSnapshotService eventSnapshotService;
    private final TicketMetrics ticketMetrics;

//    @Transactional
//    public List<TicketResponse> createTickets(CreateTicketsRequest request) {
//        EventSnapshot eventSnapshot = eventSnapshotService.resolve(request.eventId());
//        return persistTickets(
//            request.reservationId(),
//            request.userId(),
//            request.eventId(),
//            request.tickets().stream().map(this::fromPublicTicketItem).toList(),
//            eventSnapshot
//        );
//    }

    @Transactional
    public List<TicketResponse> createTickets(CreateTicketRequest request) {
        EventSnapshot eventSnapshot = eventSnapshotService.resolve(request.eventId());
        return persistTickets(
            request.reservationId(),
            request.userId(),
            request.eventId(),
            request.tickets().stream().map(this::fromSagaTicketItem).toList(),
            eventSnapshot
        );
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> getUserTickets(UUID userId) {
        return toResponses(ticketRepository.findByUserIdOrderByCreatedAtDesc(userId));
    }

    @Transactional(readOnly = true)
    public TicketResponse getTicketById(UUID ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new EntityNotFoundException("Ticket not found: " + ticketId));
        return toResponse(ticket);
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> getTicketsByReservation(UUID reservationId) {
        return toResponses(ticketRepository.findByReservationId(reservationId));
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> getOrganizerTickets(UUID eventId) {
        return toResponses(ticketRepository.findOrganizerReserved(eventId, "ACTIVE"));
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
            ticketMetrics.recordTicketConsumed();
            return toResponse(ticket);
    }

    @Transactional
    public void cancelByReservation(CancelTicketRequest request) {
        List<Ticket> tickets = ticketRepository.findByReservationId(request.reservationId());
        if (tickets.isEmpty()) {
            return;
        }

        tickets.forEach(ticket -> ticket.setReservationStatus("CANCELLED"));
        ticketRepository.saveAll(tickets);
        ticketMetrics.recordTicketCancelled();
    }

    @Transactional
    public TicketResponse reserveOrganizerTicket(UUID userId, OrganizerReserveRequest request) {
        SeatTicket ticket = new SeatTicket();
        ticket.setEventId(request.eventId());
        ticket.setUserId(userId);
        ticket.setPrice(request.price() != null ? request.price() : BigDecimal.ZERO);
        ticket.setReservationStatus("ACTIVE");
        ticket.setConsumed(false);
        ticket.setHolderName(request.holderName());
        ticket.setCategory(request.sectionName());
        ticket.setTemplateSeatId(request.templateSeatId());

        String label = request.label();
        if (label != null) {
            ticket.setRow(parseSeatRow(label));
            ticket.setSeatNumber(parseSeatNumber(label));
        }

        ticketRepository.save(ticket);
        ticketMetrics.recordTicketSold();
        return toResponse(ticket);
    }

    private List<TicketResponse> persistTickets(
        UUID reservationId,
        UUID userId,
        UUID eventId,
        List<TicketDraft> drafts,
        EventSnapshot eventSnapshot
    ) {
        return drafts.stream().map(draft -> {
            Ticket ticket = createEntity(draft);
            ticket.setReservationId(reservationId);
            ticket.setUserId(userId);
            ticket.setEventId(eventId);
            ticket.setPrice(draft.price());
            ticket.setHolderName(draft.holderName());
            ticket.setReservationStatus("ACTIVE");
            ticket.setConsumed(false);

            ticketRepository.save(ticket);
            ticketMetrics.recordTicketSold();
            return toResponse(ticket, eventSnapshot);
        }).toList();
    }

    private TicketDraft fromPublicTicketItem(CreateTicketsRequest.TicketItem item) {
        return new TicketDraft(
            item.type(),
            item.seatId(),
            item.row(),
            item.seatNumber(),
            item.sectionId(),
            item.category(),
            item.category(),
            item.category(),
            item.price(),
            null
        );
    }

    private TicketDraft fromSagaTicketItem(TicketReservationDto item) {
        return new TicketDraft(
            item.label() != null ? "SEAT" : "ZONE",
            null,
            parseSeatRow(item.label()),
            parseSeatNumber(item.label()),
            null,
            item.sectionName(),
            item.label(),
            item.sectionName(),
            item.price(),
            item.holderName()
        );
    }



    private Ticket createEntity(TicketDraft draft) {
        if ("SEAT".equalsIgnoreCase(draft.type()) || draft.row() != null) {
            SeatTicket seatTicket = new SeatTicket();
            seatTicket.setSeatId(draft.seatId());
            seatTicket.setRow(draft.row());
            seatTicket.setSeatNumber(draft.seatNumber());
            seatTicket.setCategory(defaultString(draft.category(), draft.sectionName()));
            return seatTicket;
        }

        ZoneTicket zoneTicket = new ZoneTicket();
        zoneTicket.setSectionId(draft.sectionId());
        zoneTicket.setCategory(defaultString(draft.category(), draft.sectionName()));
        return zoneTicket;
    }

    private TicketResponse toResponse(Ticket ticket) {
        return toResponse(ticket, eventSnapshotService.resolve(ticket.getEventId()));
    }

    private TicketResponse toResponse(Ticket ticket, EventSnapshot eventSnapshot) {
        TicketResponse.TicketResponseBuilder builder = TicketResponse.builder()
            .id(ticket.getId())
            .eventId(ticket.getEventId())
            .ticketType(ticket instanceof SeatTicket ? "SEAT" : "ZONE")
            .eventTitle(eventSnapshot.getTitle())
            .eventStartDate(eventSnapshot.getStartDate())
            .eventFinishDate(eventSnapshot.getFinishDate())
            .eventStatus(eventSnapshot.getStatus())
            .price(ticket.getPrice())
            .reservationStatus(ticket.getReservationStatus())
            .consumed(ticket.isConsumed())
            .holderName(ticket.getHolderName())
            .createdAt(ticket.getCreatedAt());

        if (ticket instanceof SeatTicket seatTicket) {
            builder.row(seatTicket.getRow())
                .seatNumber(seatTicket.getSeatNumber())
                .seatCategory(seatTicket.getCategory())
                .templateSeatId(seatTicket.getTemplateSeatId());
        } else if (ticket instanceof ZoneTicket zoneTicket) {
            builder.zoneCategory(zoneTicket.getCategory());
        }

        return builder.build();
    }

    private List<TicketResponse> toResponses(List<Ticket> tickets) {
        Map<UUID, EventSnapshot> snapshots = new HashMap<>();
        return tickets.stream()
            .map(ticket -> toResponse(ticket, snapshots.computeIfAbsent(ticket.getEventId(), eventSnapshotService::resolve)))
            .toList();
    }

    private Integer parseSeatNumber(String label) {
        if (label == null) {
            return null;
        }
        String digits = label.replaceAll("\\D+", "");
        if (digits.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(digits);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String parseSeatRow(String label) {
        if (label == null) {
            return null;
        }

        String text = label.replaceAll("\\d+", "").trim();

        return text.isBlank() ? null : text;
    }

    private String defaultString(String value, String fallback) {
        return value != null ? value : fallback;
    }

    private record TicketDraft(
        String type,
        UUID seatId,
        String row,
        Integer seatNumber,
        UUID sectionId,
        String category,
        String label,
        String sectionName,
        BigDecimal price,
        String holderName
    ) {}
}
