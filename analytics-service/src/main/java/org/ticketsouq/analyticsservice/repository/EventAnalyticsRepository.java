package org.ticketsouq.analyticsservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.ticketsouq.analyticsservice.model.EventAnalytics;

public interface EventAnalyticsRepository extends JpaRepository<EventAnalytics, String> {

    @Query("SELECT COALESCE(SUM(e.totalRevenue), 0) FROM EventAnalytics e WHERE e.organizationName = :orgName")
    double sumTotalRevenueByOrgName(String orgName);

    @Query("SELECT COALESCE(SUM(e.totalTicketsSold), 0) FROM EventAnalytics e WHERE e.organizationName = :orgName")
    int sumTotalTicketsSoldByOrgName(String orgName);

    @Query("SELECT COALESCE(SUM(e.capacity), 0) FROM EventAnalytics e WHERE e.organizationName = :orgName")
    int sumTotalCapacityByOrgName(String orgName);

    Page<EventAnalytics> findByOrganizationName(String orgName, Pageable pageable);

    boolean existsByOrganizationNameAndCreatedBy(String orgName, String createdBy);
}
