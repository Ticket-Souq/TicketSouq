package org.ticketsouq.notificationservice.service.impl;

import org.ticketsouq.notificationservice.service.EmailService;

import java.util.Map;

public class MockemailSender implements EmailService {

    @Override
    public void sendEmail(String to, String subject, String template, Map<String, Object> variables) {
        System.err.println("Sending email to " + to);
        System.err.println("Subject " + subject);
        System.err.println("Template " + template);
        System.err.println("Variables " + variables.toString());
    }
}
