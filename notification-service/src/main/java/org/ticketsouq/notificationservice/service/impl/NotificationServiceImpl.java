package org.ticketsouq.notificationservice.service.impl;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.ticketsouq.notificationservice.dto.NotificationResponse;
import org.ticketsouq.notificationservice.dto.UnreadCountResponse;
import org.ticketsouq.notificationservice.entity.Notification;
import org.ticketsouq.notificationservice.entity.UserEmailProjection;
import org.ticketsouq.notificationservice.enums.NotificationTemplate;
import org.ticketsouq.notificationservice.event.EmailVerificationEvent;
import org.ticketsouq.notificationservice.event.PasswordChangedEvent;
import org.ticketsouq.notificationservice.event.PasswordResetEvent;
import org.ticketsouq.notificationservice.exception.NotificationNotFoundException;
import org.ticketsouq.notificationservice.mapper.NotificationMapper;
import org.ticketsouq.notificationservice.repository.NotificationRepository;
import org.ticketsouq.notificationservice.repository.UserEmailProjectionRepository;
import org.ticketsouq.notificationservice.service.EmailService;
import org.ticketsouq.notificationservice.service.NotificationService;


import java.util.*;

@Service
public class NotificationServiceImpl implements NotificationService {
    private final EmailService emailService;
    private final NotificationRepository notificationRepository;
    private final UserEmailProjectionRepository userEmailProjectionRepository;
    private final NotificationMapper notificationMapper;

    public NotificationServiceImpl(EmailService emailService, NotificationRepository notificationRepository, UserEmailProjectionRepository userEmailProjectionRepository, NotificationMapper notificationMapper) {
        this.emailService = emailService;
        this.notificationRepository = notificationRepository;
        this.userEmailProjectionRepository = userEmailProjectionRepository;
        this.notificationMapper = notificationMapper;
    }

    @Override
    public void handleEmailVerification(EmailVerificationEvent event) {
        NotificationTemplate template = NotificationTemplate.REGISTRATION;

        Map<String, Object> variables = new HashMap<>();

        variables.put("verificationUrl", "http://localhost:8080/api/v1/auth/verify-email?token=" + event.token());

        if (!userEmailProjectionRepository.existsById(event.userId())) {
            userEmailProjectionRepository.save(new UserEmailProjection(event.userId(), event.email()));
        }
        emailService.sendEmail(event.email(), template.getEmailSubject(), template.getEmailTemplate(), variables);

    }

    @Override
    public List<NotificationResponse> getNotifications(UUID userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(notificationMapper::toResponse).toList();
    }

    @Override
    public UnreadCountResponse getUnreadCount(UUID userId) {
        long count = notificationRepository.countByUserIdAndIsReadFalse(userId);
        return notificationMapper.toUnreadCountResponse(count);
    }

    @Override
    @Transactional
    public void markAsRead(Long notificationId, UUID userId) {

        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId).orElseThrow(() -> new NotificationNotFoundException(notificationId));

        if (!notification.isRead()) {
            notification.markAsRead();
        }
    }

    @Override
    @Transactional
    public void markAllAsRead(UUID userId) {
        notificationRepository.markAllAsRead(userId);
    }

    @Override
    public void handlePasswordReset(PasswordResetEvent event) {
        System.out.println("start 5");
        UserEmailProjection user = userEmailProjectionRepository
            .findById(event.userId())
            .orElseThrow(() -> new RuntimeException("User not found"));
        System.out.println("user email " + user.getEmail());

        NotificationTemplate template = NotificationTemplate.PASSWORD_RESET;

        Map<String, Object> variables = new HashMap<>();

        variables.put(
            "resetUrl",
            "http://localhost:8080/reset-password?token=" + event.token()
        );

        emailService.sendEmail(
            user.getEmail(),
            template.getEmailSubject(),
            template.getEmailTemplate(),
            variables
        );
    }

    @Override
    public void handlePasswordChanged(PasswordChangedEvent event) {

        UserEmailProjection user = userEmailProjectionRepository
            .findById(event.userId())
            .orElseThrow(() -> new RuntimeException("User not found"));

        NotificationTemplate template = NotificationTemplate.PASSWORD_CHANGED;

        notificationRepository.save(
            Notification.create(
                event.userId(),
                template.getInAppTitle(),
                template.getInAppMessage(),
                template.getNotificationType()
            )
        );

        emailService.sendEmail(
            user.getEmail(),
            template.getEmailSubject(),
            template.getEmailTemplate(),
            Map.of()
        );
    }

}
