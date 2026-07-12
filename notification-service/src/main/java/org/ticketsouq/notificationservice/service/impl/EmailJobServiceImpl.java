package org.ticketsouq.notificationservice.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.ticketsouq.notificationservice.entity.EmailJob;
import org.ticketsouq.notificationservice.enums.NotificationTemplate;
import org.ticketsouq.notificationservice.repository.EmailJobRepository;
import org.ticketsouq.notificationservice.service.EmailJobService;
import org.ticketsouq.sharedmodule.NotificationService.exception.EmailJobSerializationException;

import java.util.Map;
import java.util.UUID;
@Service
@RequiredArgsConstructor
public class EmailJobServiceImpl implements EmailJobService {

    private final EmailJobRepository emailJobRepository;
    private final ObjectMapper objectMapper;


    @Override
    @Transactional
    public void createEmailJob(
        UUID messageId,
        String recipient,
        NotificationTemplate template,
        Map<String, Object> variables
    ) {
        try {
            String variablesJson = objectMapper.writeValueAsString(variables);

            EmailJob emailJob = EmailJob.builder()
                .messageId(messageId)
                .recipient(recipient)
                .template(template)
                .variablesJson(variablesJson)
                .build();

            emailJobRepository.save(emailJob);

        } catch (JsonProcessingException e) {
            throw new EmailJobSerializationException(e);
        }
    }
}
