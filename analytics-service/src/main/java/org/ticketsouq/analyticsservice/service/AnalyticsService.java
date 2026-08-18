package org.ticketsouq.analyticsservice.service;

import org.springframework.data.domain.Pageable;
import org.ticketsouq.analyticsservice.dto.*;

import java.util.Optional;

public interface AnalyticsService {

    OverviewKpiResponse getOverviewKpis(String userId);

    SalesPaceResponse getSalesPace(String userId, Optional<String> eventId);

    EventComparisonResponse getEventComparison(String userId, Pageable pageable);

    EventSummaryResponse getEventSummary(String eventId);

    EventSalesTimelineResponse getEventSalesTimeline(String eventId, String granularity);
}
