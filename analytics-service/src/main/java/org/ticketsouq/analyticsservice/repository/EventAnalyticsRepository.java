package org.ticketsouq.analyticsservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.ticketsouq.analyticsservice.model.EventAnalytics;

import java.util.List;

public interface EventAnalyticsRepository extends JpaRepository<EventAnalytics, String> {

    @Query("SELECT COALESCE(SUM(e.totalRevenue), 0) FROM EventAnalytics e")
    double sumTotalRevenue();

    @Query("SELECT COALESCE(SUM(e.totalTicketsSold), 0) FROM EventAnalytics e")
    int sumTotalTicketsSold();

    List<EventAnalytics> findAllByOrderByTotalRevenueDesc();
}
