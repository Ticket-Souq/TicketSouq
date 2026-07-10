package org.ticketsouq.notificationservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.ticketsouq.notificationservice.entity.Notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository
    extends JpaRepository<Notification, UUID> {
    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId);

    long countByUserIdAndIsReadFalse(UUID userId);

    Optional<Notification> findByIdAndUserId(Long id, UUID userId);

    @Modifying
    @Query("""
        UPDATE Notification n
        SET n.isRead = true
        WHERE n.userId = :userId
          AND n.isRead = false
        """)
    int markAllAsRead(UUID userId);
}
