package org.ticketsouq.ticketservice.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES;
import org.ticketsouq.sharedmodule.ReservationService.events.SagaTicketCommand;
import org.ticketsouq.sharedmodule.ReservationService.events.SagaTicketReplyEvent;
import org.ticketsouq.sharedmodule.TicketService.dto.CreateTicketRequest;
import org.ticketsouq.ticketservice.service.TicketService;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SagaTicketCommandConsumer {

    private final TicketService ticketService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = TOPIC_NAMES.SAGA_TICKET_COMMAND, groupId = "ticket-service")
    public void handleSagaTicketCommand(SagaTicketCommand command) {
        log.info("Received SagaTicketCommand for reservationId={}", command.reservationId());

        try {
            List<org.ticketsouq.sharedmodule.EventService.dto.TicketReservationDto> tickets = command.tickets();
            if (tickets == null) {
                tickets = List.of();
            }
            CreateTicketRequest request = new CreateTicketRequest(
                command.reservationId(),
                command.eventId(),
                command.userId(),
                tickets
            );
            ticketService.createTickets(request);
            sendReply(command.reservationId(), true, null);
        } catch (Exception e) {
            log.error("Failed to create tickets for reservationId={}: {}", command.reservationId(), e.getMessage(), e);
            sendReply(command.reservationId(), false, e.getMessage());
        }
    }

    private void sendReply(UUID reservationId, boolean success, String failReason) {
        SagaTicketReplyEvent reply = new SagaTicketReplyEvent(reservationId, success, failReason);
        kafkaTemplate.send(TOPIC_NAMES.SAGA_TICKET_REPLY, reservationId.toString(), reply);
        log.info("Sent SagaTicketReplyEvent for reservationId={}, success={}", reservationId, success);
    }
}