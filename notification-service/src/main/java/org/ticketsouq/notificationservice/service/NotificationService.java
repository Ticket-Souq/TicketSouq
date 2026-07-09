package org.ticketsouq.notificationservice.service;

import org.ticketsouq.notificationservice.event.EmailVerificationEvent;

public interface NotificationService {

    void handleEmailVerification(EmailVerificationEvent event);

}
