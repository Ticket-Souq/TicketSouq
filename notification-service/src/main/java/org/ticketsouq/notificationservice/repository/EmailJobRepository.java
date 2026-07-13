package org.ticketsouq.notificationservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.ticketsouq.notificationservice.entity.EmailJob;
import org.ticketsouq.notificationservice.enums.EmailJobStatus;

import java.util.List;
import java.util.UUID;

@Repository
public interface EmailJobRepository extends JpaRepository<EmailJob, UUID> {
    List<EmailJob> findTop100ByStatusOrderByCreatedAtAsc(EmailJobStatus status);
}
