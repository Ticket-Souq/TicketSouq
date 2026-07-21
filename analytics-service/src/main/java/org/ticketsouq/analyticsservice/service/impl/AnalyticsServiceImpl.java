package org.ticketsouq.analyticsservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.ticketsouq.analyticsservice.dto.*;
import org.ticketsouq.analyticsservice.service.AnalyticsService;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {

    @Override
    public OverviewKpiResponse getOverviewKpis(String range) {
        return new OverviewKpiResponse(
            new OverviewKpiResponse.RevenueKpi(182400, "USD", 12.4),
            new OverviewKpiResponse.TicketsSoldKpi(6840, 8500),
            new OverviewKpiResponse.CheckInRateKpi(91, 9),
            new OverviewKpiResponse.AvgTicketPriceKpi(26.70, "USD")
        );
    }

    @Override
    public SalesPaceResponse getSalesPace(String range, Optional<String> eventId) {
        return new SalesPaceResponse("day", List.of(
            new SalesPaceResponse.DataPoint("2026-07-06", 200),
            new SalesPaceResponse.DataPoint("2026-07-07", 450)
        ));
    }

    @Override
    public RevenueByTierResponse getRevenueByTier(String range, Optional<String> eventId) {
        return new RevenueByTierResponse(List.of(
            new RevenueByTierResponse.TierData("General", 87552, 48),
            new RevenueByTierResponse.TierData("VIP", 56544, 31),
            new RevenueByTierResponse.TierData("Early bird", 38304, 21)
        ));
    }

    @Override
    public SalesByChannelResponse getSalesByChannel(String range, Optional<String> eventId) {
        return new SalesByChannelResponse(List.of(
            new SalesByChannelResponse.ChannelData("website", 3200),
            new SalesByChannelResponse.ChannelData("social", 1800),
            new SalesByChannelResponse.ChannelData("partner", 1100),
            new SalesByChannelResponse.ChannelData("email", 740)
        ));
    }

    @Override
    public EventComparisonResponse getEventComparison(String range, String sort, int page, int pageSize) {
        return new EventComparisonResponse(List.of(
            new EventComparisonResponse.EventRow("evt_101", "Summer Fest 2026", "2026-08-22", 3120, 3600, 94300, 7)
        ), 1, 1);
    }

    @Override
    public NoShowsByTierResponse getNoShowsByTier(String range) {
        return new NoShowsByTierResponse(List.of(
            new NoShowsByTierResponse.EventTierBreakdown("evt_101", "Summer Fest 2026", List.of(
                new NoShowsByTierResponse.TierNoShow("General", 9),
                new NoShowsByTierResponse.TierNoShow("VIP", 3),
                new NoShowsByTierResponse.TierNoShow("Early bird", 6)
            ))
        ));
    }

    @Override
    public EventSummaryResponse getEventSummary(String eventId) {
        return new EventSummaryResponse(
            eventId, "Summer Fest 2026", "2026-08-22T18:00:00Z",
            new EventSummaryResponse.VenueInfo("Riverside Park", "Cairo"),
            3600,
            new EventSummaryResponse.EventKpis(
                new EventSummaryResponse.RevenueKpi(94300, 8.2),
                new EventSummaryResponse.SoldKpi(3120, 3600),
                new EventSummaryResponse.CheckInKpi(93, 7),
                new EventSummaryResponse.RefundKpi(2.1, -0.6)
            )
        );
    }

    @Override
    public EventSalesTimelineResponse getEventSalesTimeline(String eventId, String granularity) {
        String period = "week".equals(granularity) ? "2026-07-04" : "2026-07-04";
        return new EventSalesTimelineResponse(granularity, List.of(
            new EventSalesTimelineResponse.PeriodDataPoint("2026-06-27", 340),
            new EventSalesTimelineResponse.PeriodDataPoint(period, 780)
        ));
    }

    @Override
    public EventTiersResponse getEventTiers(String eventId) {
        return new EventTiersResponse(List.of(
            new EventTiersResponse.TierDetail("tier_1", "General", 1980, 2200, 39600, 9),
            new EventTiersResponse.TierDetail("tier_2", "VIP", 560, 600, 33600, 3)
        ));
    }

    @Override
    public EventChannelsResponse getEventChannels(String eventId) {
        return new EventChannelsResponse(List.of(
            new EventChannelsResponse.ChannelPercentage("website", 46),
            new EventChannelsResponse.ChannelPercentage("social", 28),
            new EventChannelsResponse.ChannelPercentage("partner", 17),
            new EventChannelsResponse.ChannelPercentage("email", 9)
        ));
    }

    @Override
    public CheckInCurveResponse getCheckInCurve(String eventId) {
        return new CheckInCurveResponse("2026-08-22", List.of(
            new CheckInCurveResponse.HourlyCheckIn("12:00", 40),
            new CheckInCurveResponse.HourlyCheckIn("13:00", 180),
            new CheckInCurveResponse.HourlyCheckIn("14:00", 420)
        ), "16:00");
    }

    @Override
    public DemographicsResponse getDemographics(String eventId) {
        return new DemographicsResponse(
            List.of(
                new DemographicsResponse.AgeGroup("18-24", 22),
                new DemographicsResponse.AgeGroup("25-34", 38),
                new DemographicsResponse.AgeGroup("35-44", 24),
                new DemographicsResponse.AgeGroup("45-54", 11),
                new DemographicsResponse.AgeGroup("55+", 5)
            ),
            List.of(
                new DemographicsResponse.LocationPct("Cairo", 61),
                new DemographicsResponse.LocationPct("Giza", 19),
                new DemographicsResponse.LocationPct("Alexandria", 12)
            )
        );
    }

    @Override
    public RefundsResponse getRefunds(String eventId, String granularity) {
        return new RefundsResponse(granularity, List.of(
            new RefundsResponse.RefundPeriod("2026-06-27", 2),
            new RefundsResponse.RefundPeriod("2026-07-04", 5)
        ), 2.1);
    }

    @Override
    public ProfitResponse getProfit(String eventId) {
        return new ProfitResponse(94300,
            new ProfitResponse.CostBreakdown(18000, 9500, 6200, 1300),
            59300, 62.9
        );
    }
}
