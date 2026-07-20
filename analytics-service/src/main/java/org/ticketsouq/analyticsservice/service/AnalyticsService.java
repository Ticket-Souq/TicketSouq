package org.ticketsouq.analyticsservice.service;

import org.ticketsouq.analyticsservice.dto.*;

import java.util.Optional;

public interface AnalyticsService {

    OverviewKpiResponse getOverviewKpis(String range);

    SalesPaceResponse getSalesPace(String range, Optional<String> eventId);

    RevenueByTierResponse getRevenueByTier(String range, Optional<String> eventId);

    SalesByChannelResponse getSalesByChannel(String range, Optional<String> eventId);

    EventComparisonResponse getEventComparison(String range, String sort, int page, int pageSize);

    NoShowsByTierResponse getNoShowsByTier(String range);

    EventSummaryResponse getEventSummary(String eventId);

    EventSalesTimelineResponse getEventSalesTimeline(String eventId, String granularity);

    EventTiersResponse getEventTiers(String eventId);

    EventChannelsResponse getEventChannels(String eventId);

    CheckInCurveResponse getCheckInCurve(String eventId);

    DemographicsResponse getDemographics(String eventId);

    RefundsResponse getRefunds(String eventId, String granularity);

    ProfitResponse getProfit(String eventId);
}
