package org.ticketsouq.analyticsservice.service;

import org.ticketsouq.analyticsservice.dto.*;

import java.util.Optional;

public interface AnalyticsService {

    OverviewKpiResponse getOverviewKpis(String orgId, String range);

    SalesPaceResponse getSalesPace(String orgId, String range, Optional<String> eventId);

    EventComparisonResponse getEventComparison(String orgId, String range, String sort, int page, int pageSize);

    EventSummaryResponse getEventSummary(String eventId);

    EventSalesTimelineResponse getEventSalesTimeline(String eventId, String granularity);
}
