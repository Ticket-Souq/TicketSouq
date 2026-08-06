package org.ticketsouq.notificationservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.ticketsouq.notificationservice.service.EmailService;
import org.ticketsouq.notificationservice.service.impl.EmailServiceImpl;
import org.ticketsouq.notificationservice.service.impl.MockemailSender;

@Component
public class EmailBean {

    @Bean
    @ConditionalOnProperty(name = "email.provider", havingValue = "MOCK", matchIfMissing = true)
    public EmailService mockEmailService() {
        return new MockemailSender();
    }

    @Bean
    @ConditionalOnProperty(name = "email.provider", havingValue = "REAL")
    public EmailService RealEmailService(JavaMailSender mailSender,TemplateEngine templateEngine) {
        return new EmailServiceImpl(mailSender,templateEngine);
    }

}
