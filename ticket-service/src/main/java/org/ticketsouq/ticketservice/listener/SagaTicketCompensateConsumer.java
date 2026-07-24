package org.ticketsouq.ticketservice.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES;
import org.ticketsouq.sharedmodule.ReservationService.events.SagaTicketCompensateCommand;
import org.ticketsouq.sharedmodule.TicketService.dto.CancelTicketRequest;
import org.ticketsouq.ticketservice.service.TicketService;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SagaTicketCompensateConsumer {

    private final TicketService ticketService;

    @KafkaListener(topics = TOPIC_NAMES.SAGA_TICKET_COMPENSATE, groupId = "ticket-service")
    public void handleSagaTicketCompensate(SagaTicketCompensateCommand command) {
        log.info("Received SagaTicketCompensateCommand for reservationId={}", command.reservationId());

        try {
            ticketService.cancelByReservation(new CancelTicketRequest(command.reservationId()));
            log.info("Cancelled tickets for reservationId={}", command.reservationId());
        } catch (Exception e) {
            log.warn("Failed to cancel tickets for reservationId={}: {}", command.reservationId(), e.getMessage());
        }
    }
}