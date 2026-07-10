package org.ticketsouq.notificationservice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.ticketsouq.notificationservice.dto.NotificationResponse;
import org.ticketsouq.notificationservice.dto.UnreadCountResponse;
import org.ticketsouq.notificationservice.security.CurrentUserProvider;
import org.ticketsouq.notificationservice.service.NotificationService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUserProvider currentUserProvider;

    public NotificationController(
        NotificationService notificationService,
        CurrentUserProvider currentUserProvider
    ) {
        this.notificationService = notificationService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public List<NotificationResponse> getNotifications() {

        return notificationService.getNotifications(
            currentUserProvider.getCurrentUserId()
        );
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse getUnreadCount() {
        return notificationService.getUnreadCount(
            currentUserProvider.getCurrentUserId()
        );
    }

    @PatchMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAsRead(@PathVariable Long id) {

        notificationService.markAsRead(id, currentUserProvider.getCurrentUserId());
    }
    @PatchMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllAsRead() {
        notificationService.markAllAsRead(
            currentUserProvider.getCurrentUserId()
        );
    }
}
