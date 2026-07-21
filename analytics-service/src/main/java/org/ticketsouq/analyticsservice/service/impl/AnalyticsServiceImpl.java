package org.ticketsouq.analyticsservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.ticketsouq.analyticsservice.dto.*;
import org.ticketsouq.analyticsservice.model.EventAnalytics;
import org.ticketsouq.analyticsservice.model.EventStatus;
import org.ticketsouq.analyticsservice.model.SalesRecord;
import org.ticketsouq.analyticsservice.repository.EventAnalyticsRepository;
import org.ticketsouq.analyticsservice.repository.EventRevenueByTierRepository;
import org.ticketsouq.analyticsservice.repository.SalesRecordRepository;
import org.ticketsouq.analyticsservice.service.AnalyticsService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {

    private final EventAnalyticsRepository eventAnalyticsRepository;
    private final SalesRecordRepository salesRecordRepository;
    private final EventRevenueByTierRepository eventRevenueByTierRepository;

    @Override
    public OverviewKpiResponse getOverviewKpis(String range) {
        double revenue = eventAnalyticsRepository.sumTotalRevenue();
        int ticketsSold = eventAnalyticsRepository.sumTotalTicketsSold();
        long totalEvents = eventAnalyticsRepository.count();

        return new OverviewKpiResponse(
            new OverviewKpiResponse.RevenueKpi(revenue, "USD", 0),
            new OverviewKpiResponse.TicketsSoldKpi(ticketsSold, (int) (ticketsSold * 1.2)),
            new OverviewKpiResponse.CheckInRateKpi(0, 0),
            new OverviewKpiResponse.AvgTicketPriceKpi(ticketsSold > 0 ? revenue / ticketsSold : 0, "USD")
        );
    }

    @Override
    public SalesPaceResponse getSalesPace(String range, Optional<String> eventId) {
        LocalDate from = parseRange(range);
        List<SalesRecord> records = salesRecordRepository.findBySaleDateBetweenOrderBySaleDateAsc(from, LocalDate.now());

        List<SalesPaceResponse.DataPoint> series = records.stream()
            .map(r -> new SalesPaceResponse.DataPoint(r.getSaleDate().toString(), r.getRevenue().intValue()))
            .toList();

        return new SalesPaceResponse("day", series);
    }

    @Override
    public RevenueByTierResponse getRevenueByTier(String range, Optional<String> eventId) {
        return new RevenueByTierResponse(List.of());
    }

    @Override
    public SalesByChannelResponse getSalesByChannel(String range, Optional<String> eventId) {
        return new SalesByChannelResponse(List.of());
    }

    @Override
    public EventComparisonResponse getEventComparison(String range, String sort, int page, int pageSize) {
        List<EventAnalytics> events = eventAnalyticsRepository.findAllByOrderByTotalRevenueDesc();

        List<EventComparisonResponse.EventRow> rows = events.stream()
            .map(e -> new EventComparisonResponse.EventRow(
                e.getEventId(),
                e.getTitle() != null ? e.getTitle() : "",
                e.getStartDateTime() != null ? e.getStartDateTime().toString() : "",
                e.getTotalTicketsSold() != null ? e.getTotalTicketsSold() : 0,
                0,
                e.getTotalRevenue() != null ? e.getTotalRevenue().doubleValue() : 0,
                0
            ))
            .toList();

        return new EventComparisonResponse(rows, page, (int) Math.ceil((double) rows.size() / pageSize));
    }

    @Override
    public NoShowsByTierResponse getNoShowsByTier(String range) {
        return new NoShowsByTierResponse(List.of());
    }

    @Override
    public EventSummaryResponse getEventSummary(String eventId) {
        Optional<EventAnalytics> opt = eventAnalyticsRepository.findById(eventId);
        if (opt.isEmpty()) {
            return new EventSummaryResponse(eventId, "Unknown", "", null, 0, null);
        }

        EventAnalytics e = opt.get();
        int ticketsSold = e.getTotalTicketsSold() != null ? e.getTotalTicketsSold() : 0;
        double revenue = e.getTotalRevenue() != null ? e.getTotalRevenue().doubleValue() : 0;

        return new EventSummaryResponse(
            e.getEventId(),
            e.getTitle() != null ? e.getTitle() : "",
            e.getStartDateTime() != null ? e.getStartDateTime().toString() : "",
            new EventSummaryResponse.VenueInfo("", ""),
            0,
            new EventSummaryResponse.EventKpis(
                new EventSummaryResponse.RevenueKpi(revenue, 0),
                new EventSummaryResponse.SoldKpi(ticketsSold, 0),
                new EventSummaryResponse.CheckInKpi(0, 0),
                new EventSummaryResponse.RefundKpi(0, 0)
            )
        );
    }

    @Override
    public EventSalesTimelineResponse getEventSalesTimeline(String eventId, String granularity) {
        List<SalesRecord> records = salesRecordRepository.findByEventIdOrderBySaleDateAsc(eventId);

        List<EventSalesTimelineResponse.PeriodDataPoint> series = records.stream()
            .map(r -> new EventSalesTimelineResponse.PeriodDataPoint(
                r.getSaleDate().toString(),
                r.getRevenue().intValue()))
            .toList();

        return new EventSalesTimelineResponse(granularity, series);
    }

    @Override
    public EventTiersResponse getEventTiers(String eventId) {
        return new EventTiersResponse(List.of());
    }

    @Override
    public EventChannelsResponse getEventChannels(String eventId) {
        return new EventChannelsResponse(List.of());
    }

    @Override
    public CheckInCurveResponse getCheckInCurve(String eventId) {
        return new CheckInCurveResponse("", List.of(), "");
    }

    @Override
    public DemographicsResponse getDemographics(String eventId) {
        return new DemographicsResponse(List.of(), List.of());
    }

    @Override
    public RefundsResponse getRefunds(String eventId, String granularity) {
        return new RefundsResponse(granularity, List.of(), 0);
    }

    @Override
    public ProfitResponse getProfit(String eventId) {
        return new ProfitResponse(0, new ProfitResponse.CostBreakdown(0, 0, 0, 0), 0, 0);
    }

    // ── helpers ──────────────────────────────────────────────

    private LocalDate parseRange(String range) {
        return switch (range) {
            case "7d" -> LocalDate.now().minusDays(7);
            case "90d" -> LocalDate.now().minusDays(90);
            case "12m" -> LocalDate.now().minusMonths(12);
            default -> LocalDate.now().minusDays(30);
        };
    }
}
