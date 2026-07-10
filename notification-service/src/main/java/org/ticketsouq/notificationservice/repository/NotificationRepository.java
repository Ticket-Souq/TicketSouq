package org.ticketsouq.notificationservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ticketsouq.notificationservice.entity.Notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository
    extends JpaRepository<Notification, UUID> {
    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId);

    long countByUserIdAndIsReadFalse(UUID userId);

    Optional<Notification> findByIdAndUserId(Long id, UUID userId);
}
