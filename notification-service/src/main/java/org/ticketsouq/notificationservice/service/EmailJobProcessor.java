package org.ticketsouq.notificationservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.ticketsouq.notificationservice.entity.EmailJob;
import org.ticketsouq.notificationservice.enums.EmailJobStatus;
import org.ticketsouq.notificationservice.repository.EmailJobRepository;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailJobProcessor {

    private static final Logger log = LoggerFactory.getLogger(EmailJobProcessor.class);

    private final EmailService emailService;
    private final ObjectMapper objectMapper;
    private final EmailJobRepository emailJobRepository;


    @Transactional
    public void process(UUID jobId) {
        EmailJob job = emailJobRepository.findById(jobId).orElseThrow(() -> new IllegalStateException("EmailJob not found"));
        try {
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

            markAsSent(job);

            log.info("Email job {} processed successfully", job.getId());
        } catch (Exception ex) {
            markAsFailed(job);
            log.error("Failed to process email job {}", job.getId(), ex);
        }
    }

    private void markAsSent(EmailJob job) {
        job.setStatus(EmailJobStatus.SENT);
        job.setLastAttemptAt(LocalDateTime.now());
    }

    private void markAsFailed(EmailJob job) {
        job.setRetryCount(job.getRetryCount() + 1);
        job.setLastAttemptAt(LocalDateTime.now());

        if (job.getRetryCount() >= 5) {
            job.setStatus(EmailJobStatus.FAILED);
        }
    }
}
