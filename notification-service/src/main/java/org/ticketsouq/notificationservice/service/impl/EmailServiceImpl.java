package org.ticketsouq.notificationservice.service.impl;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.ticketsouq.notificationservice.service.EmailService;

import java.util.Map;

@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.brand-name:Ticketaty}")
    private String brandName;

    @Value("${app.support-email:support@ticketaty.com}")
    private String supportEmail;


    @Override
    public void sendEmail(
        String to,
        String subject,
        String template,
        Map<String, Object> variables
    ) {

        Context context = new Context();
        context.setVariables(variables);
        context.setVariable("brandName", brandName);
        context.setVariable("supportEmail", supportEmail);

        subject = subject.replace("{brand}", brandName);

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
            throw new RuntimeException("Failed to send email to " + to, e);
        }
    }
}
