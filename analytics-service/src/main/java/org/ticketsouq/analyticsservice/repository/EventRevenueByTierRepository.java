package org.ticketsouq.analyticsservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ticketsouq.analyticsservice.model.EventRevenueByTier;
import org.ticketsouq.analyticsservice.model.EventRevenueByTierId;

public interface EventRevenueByTierRepository extends JpaRepository<EventRevenueByTier, EventRevenueByTierId> {
}
