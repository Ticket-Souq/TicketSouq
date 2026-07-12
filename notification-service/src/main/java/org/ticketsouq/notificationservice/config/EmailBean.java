package org.ticketsouq.notificationservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.ticketsouq.notificationservice.service.EmailService;
import org.ticketsouq.notificationservice.service.impl.EmailServiceImpl;
import org.ticketsouq.notificationservice.service.impl.MockemailSender;

@Component
@RequiredArgsConstructor
public class EmailBean {

//    private final JavaMailSender mailSender;
//    private final TemplateEngine templateEngine;

    @Bean
    public EmailService emailService() {
        return new MockemailSender();
    }
}
