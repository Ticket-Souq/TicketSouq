package org.ticketsouq.notificationservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ticketsouq.notificationservice.entity.UserEmailProjection;

import java.util.UUID;

public interface UserEmailProjectionRepository extends JpaRepository<UserEmailProjection, UUID> {
}
