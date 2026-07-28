package org.ticketsouq.ticketservice.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.ticketsouq.sharedmodule.EventService.dto.TicketReservationDto;
import org.ticketsouq.sharedmodule.EventService.events.OrganizerReservationCancelledEvent;
import org.ticketsouq.sharedmodule.EventService.events.OrganizerReservationCreatedEvent;
import org.ticketsouq.ticketservice.models.SeatTicket;
import org.ticketsouq.ticketservice.models.Ticket;
import org.ticketsouq.ticketservice.repository.TicketRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrganizerReservationConsumer {

    private final TicketRepository ticketRepository;

    private static final Pattern SEAT_LABEL_PATTERN = Pattern.compile("^([A-Za-z]+)(\\d+)$");

    @KafkaListener(topics = "organizer.reservation.created", groupId = "ticket-service")
    public void handleOrganizerReservationCreated(OrganizerReservationCreatedEvent event) {
        log.info("Received OrganizerReservationCreatedEvent for eventId={}, reservations={}",
            event.eventId(), event.reservations() != null ? event.reservations().size() : 0);

        if (event.reservations() == null || event.reservations().isEmpty()) {
            return;
        }

        List<Ticket> tickets = event.reservations().stream()
            .<Ticket>map(reservation -> buildSeatTicket(event.eventId(), event.createdBy(), reservation))
            .toList();

        ticketRepository.saveAll(tickets);
        log.info("Created {} organizer tickets for eventId={}", tickets.size(), event.eventId());
    }

    @KafkaListener(topics = "organizer.reservation.cancelled", groupId = "ticket-service")
    public void handleOrganizerReservationCancelled(OrganizerReservationCancelledEvent event) {
        log.info("Received OrganizerReservationCancelledEvent for eventId={}", event.eventId());

        List<Ticket> tickets = ticketRepository.findByEventIdAndReservationStatus(event.eventId(), "ACTIVE");
        if (tickets.isEmpty()) {
            log.info("No active organizer tickets found for eventId={}", event.eventId());
            return;
        }

        tickets.forEach(ticket -> ticket.setReservationStatus("CANCELLED"));
        ticketRepository.saveAll(tickets);
        log.info("Cancelled {} organizer tickets for eventId={}", tickets.size(), event.eventId());
    }

    private SeatTicket buildSeatTicket(UUID eventId, UUID userId, TicketReservationDto reservation) {
        SeatTicket ticket = new SeatTicket();
        ticket.setEventId(eventId);
        ticket.setUserId(userId);
        ticket.setPrice(reservation.price() != null ? reservation.price() : BigDecimal.ZERO);
        ticket.setReservationStatus("ACTIVE");
        ticket.setConsumed(false);
        ticket.setHolderName(reservation.holderName());
        ticket.setCategory(reservation.sectionName());

        String label = reservation.label();
        if (label != null) {
            Matcher matcher = SEAT_LABEL_PATTERN.matcher(label);
            if (matcher.matches()) {
                ticket.setRow(matcher.group(1));
                ticket.setSeatNumber(Integer.parseInt(matcher.group(2)));
            } else {
                ticket.setRow(label);
                ticket.setSeatNumber(null);
            }
        }

        return ticket;
    }
}
