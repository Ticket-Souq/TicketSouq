package org.ticketsouq.notificationservice.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.ticketsouq.notificationservice.event.EmailVerificationEvent;
import org.ticketsouq.notificationservice.service.EmailService;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.thymeleaf.context.Context;

@Service
public class EmailServiceImpl implements EmailService {
    private static final Logger log =
        LoggerFactory.getLogger(EmailServiceImpl.class);
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    public EmailServiceImpl(JavaMailSender mailSender,
                            TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    @Override
    public void sendVerificationEmail(EmailVerificationEvent event) {
        log.info("Step 1");

        Context context = new Context();

        log.info("Step 2");

        String verificationUrl = "...";

        context.setVariable("verificationUrl", verificationUrl);

        log.info("Step 3");

        String html = templateEngine.process("email/registration", context);

        log.info("Step 4");

        MimeMessage mimeMessage = mailSender.createMimeMessage();

        log.info("Step 5");

        try {

            log.info("Step 6");

            MimeMessageHelper helper =
                new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setTo(event.email());

            log.info("Step 7");

            helper.setSubject("Verify your Ticketaty account");

            helper.setText(html, true);

            log.info("Step 8");

            mailSender.send(mimeMessage);

            log.info("Email Sent Successfully");

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
