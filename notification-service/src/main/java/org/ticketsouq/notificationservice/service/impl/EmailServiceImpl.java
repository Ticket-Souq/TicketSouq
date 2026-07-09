package org.ticketsouq.notificationservice.service.impl;

import org.springframework.stereotype.Service;
import org.ticketsouq.notificationservice.event.EmailVerificationEvent;
import org.ticketsouq.notificationservice.service.EmailService;

@Service
public class EmailServiceImpl implements EmailService {

    @Override
    public void sendVerificationEmail(EmailVerificationEvent event) {
        System.out.println("================================");
        System.out.println("Sending verification email");
        System.out.println("User: " + event.email());
        System.out.println("Token: " + event.token());
        System.out.println("================================");
    }
}
