package org.ticketsouq.venueservice.repos;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.ticketsouq.venueservice.model.Venue;

import java.util.UUID;

public interface VenueRepository extends JpaRepository<Venue, UUID> {
    Page<Venue> findByOrgId(UUID orgId, Pageable pageable);
}
