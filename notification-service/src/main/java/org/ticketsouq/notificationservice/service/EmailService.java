package org.ticketsouq.notificationservice.service;


import java.util.Map;

public interface EmailService {

    void sendEmail(
        String to,
        String subject,
        String template,
        Map<String, Object> variables
    );
}
