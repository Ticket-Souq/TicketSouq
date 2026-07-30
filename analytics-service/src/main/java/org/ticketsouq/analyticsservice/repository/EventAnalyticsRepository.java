package org.ticketsouq.analyticsservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.ticketsouq.analyticsservice.model.EventAnalytics;

public interface EventAnalyticsRepository extends JpaRepository<EventAnalytics, String> {

    @Query("SELECT COALESCE(SUM(e.totalRevenue), 0) FROM EventAnalytics e WHERE e.organizationId = :orgId")
    double sumTotalRevenueByOrg(String orgId);

    @Query("SELECT COALESCE(SUM(e.totalTicketsSold), 0) FROM EventAnalytics e WHERE e.organizationId = :orgId")
    int sumTotalTicketsSoldByOrg(String orgId);

    @Query("SELECT COALESCE(SUM(e.capacity), 0) FROM EventAnalytics e WHERE e.organizationId = :orgId")
    int sumTotalCapacityByOrg(String orgId);

    Page<EventAnalytics> findByOrganizationId(String orgId, Pageable pageable);

    boolean existsByOrganizationIdAndCreatedBy(String orgId, String createdBy);
}
