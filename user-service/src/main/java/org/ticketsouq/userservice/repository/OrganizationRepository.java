package org.ticketsouq.userservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.ticketsouq.userservice.model.OrgStatus;
import org.ticketsouq.userservice.model.Organization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    boolean existsByName(String name);

    Optional<Organization> findByName(String name);

    List<Organization> findByStatus(OrgStatus status);
}
