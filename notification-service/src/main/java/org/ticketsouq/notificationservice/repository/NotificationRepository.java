package org.ticketsouq.notificationservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ticketsouq.notificationservice.entity.Notification;

import java.util.UUID;

public interface NotificationRepository
    extends JpaRepository<Notification, UUID> {
}
