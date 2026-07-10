package org.ticketsouq.notificationservice.service.impl;

import org.springframework.stereotype.Service;
import org.ticketsouq.notificationservice.service.EmailService;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.thymeleaf.context.Context;

import java.util.Map;

@Service
public class EmailServiceImpl implements EmailService {
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    public EmailServiceImpl(JavaMailSender mailSender,
                            TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    @Override
    public void sendEmail(
        String to,
        String subject,
        String template,
        Map<String, Object> variables
    ) {

        Context context = new Context();
        context.setVariables(variables);

        String html = templateEngine.process(template, context);

        MimeMessage mimeMessage = mailSender.createMimeMessage();

        try {
            MimeMessageHelper helper =
                new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);

            mailSender.send(mimeMessage);

        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }
}
