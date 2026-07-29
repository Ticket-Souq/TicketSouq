package org.ticketsouq.apigateway.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;
import org.ticketsouq.sharedmodule.ApiGateway.event.AccountsGeneratedEvent;
import org.ticketsouq.sharedmodule.ApiGateway.event.EmailVerificationEvent;
import org.ticketsouq.sharedmodule.ApiGateway.event.PasswordResetEvent;
import org.ticketsouq.sharedmodule.AuditService.events.AuditEvent;
import org.ticketsouq.sharedmodule.utils.LogUtils;

import static org.ticketsouq.sharedmodule.Constants.SERVICE_NAMES.API_GATEWAY;
import static org.ticketsouq.sharedmodule.Constants.TOPIC_NAMES.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthEventPublisher {

    private final KafkaCircuitBreakerWrapper kafka;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendVerificationEmail(EmailVerificationEvent event) {
        LogUtils.logEventPublished(API_GATEWAY,USER_EMAIL_VERIFICATION);
        kafka.send(USER_EMAIL_VERIFICATION, event.userId().toString(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendPasswordResetEmail(PasswordResetEvent event) {
        LogUtils.logEventPublished(API_GATEWAY,USER_PASSWORD_RESET);
        kafka.send(USER_PASSWORD_RESET, event.userId().toString(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendAuditEvent(AuditEvent event) {
        LogUtils.logEventPublished(API_GATEWAY,AUDIT_EVENT);
        kafka.send(AUDIT_EVENT, event.madeById().toString(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendAccountsGeneratedEmail(AccountsGeneratedEvent event) {
        LogUtils.logEventPublished(API_GATEWAY,ACCOUNTS_GENERATED);
        kafka.send(ACCOUNTS_GENERATED, event.orgHeadUserId().toString(), event);
    }

}
