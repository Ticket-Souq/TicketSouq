package org.ticketsouq.notificationservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.ticketsouq.notificationservice.entity.EmailJob;
import org.ticketsouq.notificationservice.repository.EmailJobRepository;

import java.util.Map;
import java.util.UUID;

@Service
public class EmailJobProcessor {

    private static final Logger log = LoggerFactory.getLogger(EmailJobProcessor.class);

    private final EmailService emailService;
    private final ObjectMapper objectMapper;
    private final EmailJobRepository emailJobRepository;


    public EmailJobProcessor(
        EmailService emailService,
        ObjectMapper objectMapper,
        EmailJobRepository emailJobRepository
    ) {
        this.emailService = emailService;
        this.objectMapper = objectMapper;
        this.emailJobRepository = emailJobRepository;
    }

    @Transactional
    public void process(UUID jobId) {

        try {
            EmailJob job = emailJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("EmailJob not found"));
            Map<String, Object> variables = objectMapper.readValue(
                job.getVariablesJson(),
                new TypeReference<>() {}
            );

            emailService.sendEmail(
                job.getRecipient(),
                job.getTemplate().getEmailSubject(),
                job.getTemplate().getEmailTemplate(),
                variables
            );
            log.info("Email sent");

            job.markAsSent();

            log.info("Email job {} processed successfully", job.getId());

        } catch (Exception ex) {
            EmailJob job = emailJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("EmailJob not found"));
            job.markAsFailed();

            log.error(
                "Failed to process email job {}",
                job.getId(),
                ex
            );
        }
    }
}
