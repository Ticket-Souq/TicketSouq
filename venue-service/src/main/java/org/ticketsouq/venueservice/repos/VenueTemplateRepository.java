package org.ticketsouq.venueservice.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ticketsouq.venueservice.model.VenueTemplate;

import java.util.List;
import java.util.UUID;

public interface VenueTemplateRepository extends JpaRepository<VenueTemplate, UUID> {
    List<VenueTemplate> findByVenueId(UUID venueId);
}
