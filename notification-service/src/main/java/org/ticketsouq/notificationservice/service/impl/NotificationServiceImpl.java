package org.ticketsouq.notificationservice.service.impl;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.ticketsouq.notificationservice.dto.EventDetailsResponse;
import org.ticketsouq.notificationservice.dto.NotificationResponse;
import org.ticketsouq.notificationservice.dto.UnreadCountResponse;
import org.ticketsouq.notificationservice.entity.Notification;
import org.ticketsouq.notificationservice.entity.UserEmailProjection;
import org.ticketsouq.notificationservice.enums.NotificationTemplate;
import org.ticketsouq.notificationservice.event.*;
import org.ticketsouq.notificationservice.exception.NotificationNotFoundException;
import org.ticketsouq.notificationservice.exception.UserEmailProjectionNotFoundException;
import org.ticketsouq.notificationservice.mapper.NotificationMapper;
import org.ticketsouq.notificationservice.repository.NotificationRepository;
import org.ticketsouq.notificationservice.repository.UserEmailProjectionRepository;
import org.ticketsouq.notificationservice.service.EmailJobService;
import org.ticketsouq.notificationservice.service.EventDetailsService;
import org.ticketsouq.notificationservice.service.NotificationService;


import java.util.*;

@Service
public class NotificationServiceImpl implements NotificationService {
    private final EmailJobService emailJobService;
    private final NotificationRepository notificationRepository;
    private final UserEmailProjectionRepository userEmailProjectionRepository;
    private final NotificationMapper notificationMapper;
    private final EventDetailsService eventDetailsService;

    public NotificationServiceImpl(NotificationRepository notificationRepository, UserEmailProjectionRepository userEmailProjectionRepository, NotificationMapper notificationMapper, EventDetailsService eventDetailsService, EmailJobService emailJobService) {
        this.notificationRepository = notificationRepository;
        this.userEmailProjectionRepository = userEmailProjectionRepository;
        this.notificationMapper = notificationMapper;
        this.eventDetailsService = eventDetailsService;
        this.emailJobService = emailJobService;
    }

    @Override
    @Transactional
    public void handleEmailVerification(EmailVerificationEvent event) {
        NotificationTemplate template = NotificationTemplate.REGISTRATION;

        Map<String, Object> variables = new HashMap<>();

        variables.put("verificationUrl", "http://localhost:8080/api/v1/auth/verify-email?token=" + event.token());

        if (!userEmailProjectionRepository.existsById(event.userId())) {
            userEmailProjectionRepository.save(new UserEmailProjection(event.userId(), event.email()));
        }
        emailJobService.createEmailJob(
            event.messageId(),
            event.email(),
            template,
            variables
        );

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
    @Transactional
    public void handlePasswordReset(PasswordResetEvent event) {
        UserEmailProjection user = userEmailProjectionRepository
            .findById(event.userId())
            .orElseThrow(() -> new RuntimeException("User not found"));

        NotificationTemplate template = NotificationTemplate.PASSWORD_RESET;

        Map<String, Object> variables = new HashMap<>();

        variables.put(
            "resetUrl",
            "http://localhost:8080/reset-password?token=" + event.token()
        );

        emailJobService.createEmailJob(
            event.messageId(),
            user.getEmail(),
            template,
            variables
        );
    }

    @Override
    @Transactional
    public void handlePasswordChanged(PasswordChangedEvent event) {

        UserEmailProjection user = userEmailProjectionRepository
            .findById(event.userId())
            .orElseThrow(() ->
                new UserEmailProjectionNotFoundException(event.userId())
            );

        NotificationTemplate template = NotificationTemplate.PASSWORD_CHANGED;

        notificationRepository.save(
            Notification.create(
                event.userId(),
                template.getInAppTitle(),
                template.getInAppMessage(),
                template.getNotificationType()
            )
        );

        emailJobService.createEmailJob(
            event.messageId(),
            user.getEmail(),
            template,
            Map.of()
        );
    }

    @Override
    @Transactional
    public void handlePaymentSuccess(PaymentSuccessEvent event) {
        NotificationTemplate template = NotificationTemplate.PAYMENT_SUCCESS;

        UserEmailProjection user = userEmailProjectionRepository
            .findById(event.userId())
            .orElseThrow(() ->
                new UserEmailProjectionNotFoundException(event.userId())
            );

        EventDetailsResponse eventDetailsResponse = eventDetailsService.getEvent(event.eventId());
        notificationRepository.save(
            Notification.create(
                event.userId(),
                template.getInAppTitle(),
                template.getInAppMessage(),
                template.getNotificationType()
            )
        );
        Map<String, Object> variables = new HashMap<>();
        variables.put("eventName", eventDetailsResponse.name());
        variables.put("location", eventDetailsResponse.location());
        variables.put("date", eventDetailsResponse.startDate());
        variables.put("amount", event.amount());
        emailJobService.createEmailJob(
            event.messageId(),
            user.getEmail(),
            template,
            variables
        );

    }

    @Override
    @Transactional
    public void handleAccountGenerated(AccountGeneratedEvent event) {

        NotificationTemplate template = NotificationTemplate.ACCOUNT_GENERATED;

        UserEmailProjection orgHead = userEmailProjectionRepository
            .findById(event.orgHeadUserId())
            .orElseThrow(() ->
                new UserEmailProjectionNotFoundException(event.orgHeadUserId())
            );

        Map<String, Object> variables = new HashMap<>();
        variables.put("accounts", event.accounts());
        variables.put("loginUrl", "http://localhost:3000/login");

        emailJobService.createEmailJob(
            event.messageId(),
            orgHead.getEmail(),
            template,
            variables
        );
    }

    @Override
    @Transactional
    public void handleRefundCompleted(RefundCompletedEvent event) {
        NotificationTemplate template = NotificationTemplate.EVENT_CANCELLED;

        UserEmailProjection user = userEmailProjectionRepository
            .findById(event.userId())
            .orElseThrow(() ->
                new UserEmailProjectionNotFoundException(event.userId())
            );
        EventDetailsResponse eventDetailsResponse = eventDetailsService.getEvent(event.eventId());
        notificationRepository.save(
            Notification.create(
                event.userId(),
                template.getInAppTitle(),
                template.getInAppMessage(),
                template.getNotificationType()
            )
        );
        Map<String, Object> variables = new HashMap<>();
        variables.put("eventName", eventDetailsResponse.name());
        variables.put("location", eventDetailsResponse.location());
        variables.put("date", eventDetailsResponse.startDate());
        variables.put("amount", event.amount());

        emailJobService.createEmailJob(
            event.messageId(),
            user.getEmail(),
            template,
            variables
        );

    }
}
