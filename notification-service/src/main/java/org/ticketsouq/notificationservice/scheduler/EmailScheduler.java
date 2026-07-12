package org.ticketsouq.notificationservice.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.ticketsouq.notificationservice.entity.EmailJob;
import org.ticketsouq.notificationservice.enums.EmailJobStatus;
import org.ticketsouq.notificationservice.repository.EmailJobRepository;
import org.ticketsouq.notificationservice.service.EmailJobProcessor;

import java.util.List;

@Component
@RequiredArgsConstructor
public class EmailScheduler {

    private final EmailJobRepository emailJobRepository;
    private final EmailJobProcessor emailJobProcessor;

    @Scheduled(fixedDelay = 30000)
    public void processPendingEmails() {

        emailJobRepository.findTop100ByStatusOrderByCreatedAtAsc(EmailJobStatus.PENDING)
            .forEach(
                emailJob -> emailJobProcessor.process(emailJob.getId())
            );

    }

}
