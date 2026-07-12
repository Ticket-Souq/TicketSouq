package org.ticketsouq.notificationservice.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.ticketsouq.notificationservice.entity.EmailJob;
import org.ticketsouq.notificationservice.enums.EmailJobStatus;
import org.ticketsouq.notificationservice.repository.EmailJobRepository;
import org.ticketsouq.notificationservice.service.EmailJobProcessor;

import java.util.List;

@Component
public class EmailScheduler {
    private final EmailJobRepository emailJobRepository;

    private final EmailJobProcessor emailJobProcessor;

    public EmailScheduler(EmailJobRepository emailJobRepository,EmailJobProcessor emailJobProcessor) {
        this.emailJobRepository = emailJobRepository;
        this.emailJobProcessor = emailJobProcessor;
    }
    @Scheduled(fixedDelay = 30000)
    public void processPendingEmails() {

        List<EmailJob> jobs =
            emailJobRepository.findTop100ByStatusOrderByCreatedAtAsc(EmailJobStatus.PENDING);

        for (EmailJob job : jobs) {
            emailJobProcessor.process(job.getId());
        }
    }

}
