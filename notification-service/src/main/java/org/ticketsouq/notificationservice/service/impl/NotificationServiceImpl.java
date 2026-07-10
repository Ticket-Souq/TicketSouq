package org.ticketsouq.notificationservice.service.impl;

import org.springframework.stereotype.Service;
import org.ticketsouq.notificationservice.entity.Notification;
import org.ticketsouq.notificationservice.enums.NotificationTemplate;
import org.ticketsouq.notificationservice.event.EmailVerificationEvent;
import org.ticketsouq.notificationservice.repository.NotificationRepository;
import org.ticketsouq.notificationservice.service.EmailService;
import org.ticketsouq.notificationservice.service.NotificationService;


import java.util.HashMap;
import java.util.Map;

@Service
public class NotificationServiceImpl implements NotificationService {
    private final EmailService emailService;
    private final NotificationRepository notificationRepository;
    public NotificationServiceImpl(EmailService emailService, NotificationRepository notificationRepository) {
        this.emailService = emailService;
        this.notificationRepository = notificationRepository;
    }

    @Override
    public void handleEmailVerification(EmailVerificationEvent event) {
        NotificationTemplate template = NotificationTemplate.REGISTRATION;

        Map<String, Object> variables = new HashMap<>();

        variables.put(
            "verificationUrl",
            "http://localhost:8080/api/v1/auth/verify-email?token=" + event.token()
        );
        notificationRepository.save(
            Notification.create(
                event.userId(),
                template.getInAppTitle(),
                template.getInAppMessage(),
                template.getNotificationType()
            )
        );
        emailService.sendEmail(
            event.email(),
            template.getEmailSubject(),
            template.getEmailTemplate(),
            variables
        );

    }
}
