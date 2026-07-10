package org.ticketsouq.notificationservice.service.impl;

import org.springframework.stereotype.Service;
import org.ticketsouq.notificationservice.entity.UserEmailProjection;
import org.ticketsouq.notificationservice.event.EmailVerificationEvent;
import org.ticketsouq.notificationservice.repository.UserEmailProjectionRepository;
import org.ticketsouq.notificationservice.service.EmailService;
import org.ticketsouq.notificationservice.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class NotificationServiceImpl implements NotificationService {
    private final UserEmailProjectionRepository userEmailProjectionRepository;
    private final EmailService emailService;
    private static final Logger log =
        LoggerFactory.getLogger(NotificationServiceImpl.class);

    public NotificationServiceImpl(UserEmailProjectionRepository userEmailProjectionRepository, EmailService emailService) {
        this.userEmailProjectionRepository = userEmailProjectionRepository;
        this.emailService = emailService;
    }

    @Override
    public void handleEmailVerification(EmailVerificationEvent event) {
        if (!userEmailProjectionRepository.existsById(event.userId())) {
            UserEmailProjection projection = UserEmailProjection.builder()
                .userId(event.userId())
                .email(event.email())
                .build();
            userEmailProjectionRepository.save(projection);
            log.info("Email projection saved for user {}", event.userId());
        }
        log.info("Email projection saved for user {}", event.userId());
        emailService.sendVerificationEmail(event);
        log.info("Verification email sent to {}", event.email());
    }
}
