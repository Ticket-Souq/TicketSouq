package org.ticketsouq.notificationservice.service.impl;

import org.springframework.stereotype.Service;
import org.ticketsouq.notificationservice.enums.EmailTemplate;
import org.ticketsouq.notificationservice.event.EmailVerificationEvent;
import org.ticketsouq.notificationservice.service.EmailService;
import org.ticketsouq.notificationservice.service.NotificationService;


import java.util.HashMap;
import java.util.Map;

@Service
public class NotificationServiceImpl implements NotificationService {
    private final EmailService emailService;

    public NotificationServiceImpl(EmailService emailService) {
        this.emailService = emailService;
    }

    @Override
    public void handleEmailVerification(EmailVerificationEvent event) {
        EmailTemplate template = EmailTemplate.REGISTRATION;

        Map<String, Object> variables = new HashMap<>();

        variables.put(
            "verificationUrl",
            "http://localhost:8080/api/v1/auth/verify-email?token=" + event.token()
        );

        emailService.sendEmail(
            event.email(),
            template.getSubject(),
            template.getTemplate(),
            variables
        );

    }
}
