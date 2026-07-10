package org.ticketsouq.apigateway.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.ticketsouq.sharedmodule.ApiGateway.event.EmailVerificationEvent;
import org.ticketsouq.sharedmodule.ApiGateway.event.PasswordResetEvent;
import org.ticketsouq.sharedmodule.utils.LogUtils;

import java.util.UUID;

import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.USER_EMAIL_VERIFICATION;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.USER_PASSWORD_RESET;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendVerificationEmail(EmailVerificationEvent event) {
        LogUtils.log(USER_EMAIL_VERIFICATION, event.userId());
        kafkaTemplate.send(USER_EMAIL_VERIFICATION, event.userId().toString(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendPasswordResetEmail(PasswordResetEvent event) {
        LogUtils.log(USER_PASSWORD_RESET, event.userId());
        kafkaTemplate.send(USER_PASSWORD_RESET, event.userId().toString(), event);
    }
}
