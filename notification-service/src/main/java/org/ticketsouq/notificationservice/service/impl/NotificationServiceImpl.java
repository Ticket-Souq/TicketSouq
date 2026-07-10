package org.ticketsouq.notificationservice.service.impl;

import org.springframework.stereotype.Service;
import org.ticketsouq.notificationservice.dto.NotificationResponse;
import org.ticketsouq.notificationservice.dto.UnreadCountResponse;
import org.ticketsouq.notificationservice.entity.Notification;
import org.ticketsouq.notificationservice.entity.UserEmailProjection;
import org.ticketsouq.notificationservice.enums.NotificationTemplate;
import org.ticketsouq.notificationservice.event.EmailVerificationEvent;
import org.ticketsouq.notificationservice.repository.NotificationRepository;
import org.ticketsouq.notificationservice.repository.UserEmailProjectionRepository;
import org.ticketsouq.notificationservice.service.EmailService;
import org.ticketsouq.notificationservice.service.NotificationService;


import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationServiceImpl implements NotificationService {
    private final EmailService emailService;
    private final NotificationRepository notificationRepository;
    private final UserEmailProjectionRepository userEmailProjectionRepository;
    public NotificationServiceImpl(EmailService emailService, NotificationRepository notificationRepository, UserEmailProjectionRepository userEmailProjectionRepository) {
        this.emailService = emailService;
        this.notificationRepository = notificationRepository;
        this.userEmailProjectionRepository = userEmailProjectionRepository;
  }
  @Override
    public void handleEmailVerification(EmailVerificationEvent event) {
        NotificationTemplate template = NotificationTemplate.REGISTRATION;

        Map<String, Object> variables = new HashMap<>();

        variables.put(
            "verificationUrl",
            "http://localhost:8080/api/v1/auth/verify-email?token=" + event.token()
        );
      if (!userEmailProjectionRepository.existsById(event.userId())) {
          userEmailProjectionRepository.save(
              new UserEmailProjection(event.userId(), event.email())
          );
      }        emailService.sendEmail(
            event.email(),
            template.getEmailSubject(),
            template.getEmailTemplate(),
            variables
        );

    }

    @Override
    public List<NotificationResponse> getNotifications(UUID userId) {
             return notificationRepository
            .findByUserIdOrderByCreatedAtDesc(userId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    public UnreadCountResponse getUnreadCount(UUID userId) {
        long count = notificationRepository.countByUserIdAndIsReadFalse(userId);
        return new UnreadCountResponse(count);
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
            notification.getId(),
            notification.getTitle(),
            notification.getMessage(),
            notification.getType(),
            notification.isRead(),
            notification.getCreatedAt()
        );
    }

}
