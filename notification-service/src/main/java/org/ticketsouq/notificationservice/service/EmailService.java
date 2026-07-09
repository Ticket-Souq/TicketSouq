package org.ticketsouq.notificationservice.service;

import org.ticketsouq.notificationservice.event.EmailVerificationEvent;

public interface EmailService {

    void sendVerificationEmail(EmailVerificationEvent event);

}
