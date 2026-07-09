package org.ticketsouq.notificationservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ticketsouq.notificationservice.entity.Notification;

public interface NotificationRepository
    extends JpaRepository<Notification, Long> {
}
