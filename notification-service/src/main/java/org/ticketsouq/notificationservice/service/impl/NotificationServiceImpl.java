package org.ticketsouq.notificationservice.service.impl;

import org.springframework.stereotype.Service;
import org.ticketsouq.notificationservice.entity.UserEmailProjection;
import org.ticketsouq.notificationservice.event.EmailVerificationEvent;
import org.ticketsouq.notificationservice.repository.UserEmailProjectionRepository;
import org.ticketsouq.notificationservice.service.EmailService;
import org.ticketsouq.notificationservice.service.NotificationService;


@Service
public class NotificationServiceImpl implements NotificationService {
    private final UserEmailProjectionRepository userEmailProjectionRepository;
    private final EmailService emailService;
    public NotificationServiceImpl(UserEmailProjectionRepository userEmailProjectionRepository, EmailService emailService) {
        this.userEmailProjectionRepository = userEmailProjectionRepository;
        this.emailService = emailService;
    }
    @Override
    public void handleEmailVerification(EmailVerificationEvent event) {

        UserEmailProjection projection = new UserEmailProjection();
        projection.setUserId(event.userId());
        projection.setEmail(event.email());

        userEmailProjectionRepository.save(projection);
        emailService.sendVerificationEmail(event);

    }
}
