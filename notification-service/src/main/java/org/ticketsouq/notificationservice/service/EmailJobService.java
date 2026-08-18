package org.ticketsouq.notificationservice.service;

import org.ticketsouq.notificationservice.enums.NotificationTemplate;

import java.util.Map;
import java.util.UUID;

public interface EmailJobService {

    void createEmailJob(
        UUID messageId,
        String recipient,
        NotificationTemplate template,
        Map<String, Object> variables
    );
}
