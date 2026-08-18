package org.ticketsouq.eventservice.event;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.eventservice.service.LockService;
import org.ticketsouq.outbox.service.OutboxWriter;
import org.ticketsouq.sharedmodule.EventService.exception.LockExpiredException;
import org.ticketsouq.sharedmodule.EventService.exception.SeatAlreadyBookedException;
import org.ticketsouq.sharedmodule.ReservationService.events.SagaLockConfirmCommand;
import org.ticketsouq.sharedmodule.ReservationService.events.SagaLockConfirmCompensateCommand;
import org.ticketsouq.sharedmodule.ReservationService.events.SagaLockConfirmReplyEvent;
import org.ticketsouq.sharedmodule.utils.LogUtils;

import static org.ticketsouq.sharedmodule.Constants.SERVICE_NAMES.EVENT_SERVICE;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.SAGA_LOCK_CONFIRM_COMMAND;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.SAGA_LOCK_CONFIRM_COMPENSATE;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.SAGA_LOCK_CONFIRM_REPLY;

@Component
@RequiredArgsConstructor
public class EventEventConsumer {

    private final LockService lockService;
    private final OutboxWriter outboxWriter;

    @KafkaListener(topics = SAGA_LOCK_CONFIRM_COMMAND)
    @Transactional
    public void lockConfirmCommandConsumer(SagaLockConfirmCommand command) {
        LogUtils.logEventConsumed(EVENT_SERVICE, SAGA_LOCK_CONFIRM_COMMAND);
        try {
            lockService.confirm(String.valueOf(command.reservationId()));
            outboxWriter.save(new SagaLockConfirmReplyEvent(command.reservationId(), true, ""), SAGA_LOCK_CONFIRM_REPLY, command.reservationId().toString());
        } catch (LockExpiredException | SeatAlreadyBookedException ex) {
            outboxWriter.save(new SagaLockConfirmReplyEvent(command.reservationId(), false, ex.getMessage()), SAGA_LOCK_CONFIRM_REPLY, command.reservationId().toString());
        }
    }

    @KafkaListener(topics = SAGA_LOCK_CONFIRM_COMPENSATE)
    @Transactional
    public void lockConfirmCompensateConsumer(SagaLockConfirmCompensateCommand command) {
        LogUtils.logEventConsumed(EVENT_SERVICE, SAGA_LOCK_CONFIRM_COMPENSATE);
        lockService.release(String.valueOf(command.reservationId()));
    }


}
