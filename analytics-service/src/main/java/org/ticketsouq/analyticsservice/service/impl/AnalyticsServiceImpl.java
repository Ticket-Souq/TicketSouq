package org.ticketsouq.analyticsservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.ticketsouq.analyticsservice.dto.*;
import org.ticketsouq.analyticsservice.model.EventAnalytics;
import org.ticketsouq.analyticsservice.model.SalesRecord;
import org.ticketsouq.analyticsservice.repository.EventAnalyticsRepository;
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

    @Override
    public OverviewKpiResponse getOverviewKpis(String range) {
        double revenue = eventAnalyticsRepository.sumTotalRevenue();
        int ticketsSold = eventAnalyticsRepository.sumTotalTicketsSold();
        int totalCapacity = eventAnalyticsRepository.sumTotalCapacity();

        return new OverviewKpiResponse(
            new OverviewKpiResponse.RevenueKpi(revenue, "USD", 0),
            new OverviewKpiResponse.TicketsSoldKpi(ticketsSold, totalCapacity),
            new OverviewKpiResponse.CheckInRateKpi(null, null),
            new OverviewKpiResponse.AvgTicketPriceKpi(ticketsSold > 0 ? revenue / ticketsSold : 0, "USD")
        );
    }

    @Override
    public SalesPaceResponse getSalesPace(String range, Optional<String> eventId) {
        LocalDate from = parseRange(range);
        LocalDate to = LocalDate.now();
        List<SalesRecord> records = eventId
            .map(id -> salesRecordRepository.findByEventIdAndSaleDateBetweenOrderBySaleDateAsc(id, from, to))
            .orElseGet(() -> salesRecordRepository.findBySaleDateBetweenOrderBySaleDateAsc(from, to));

        List<SalesPaceResponse.DataPoint> series = records.stream()
            .map(r -> new SalesPaceResponse.DataPoint(r.getSaleDate().toString(),
                r.getTicketsSold() != null ? r.getTicketsSold() : 0))
            .toList();

        return new SalesPaceResponse("day", series);
    }

    @Override
    public EventComparisonResponse getEventComparison(String range, String sort, int page, int pageSize) {
        Sort sorting = switch (sort) {
            case "revenue" -> Sort.by(Sort.Direction.DESC, "totalRevenue");
            case "tickets" -> Sort.by(Sort.Direction.DESC, "totalTicketsSold");
            case "name" -> Sort.by(Sort.Direction.ASC, "title");
            default -> Sort.by(Sort.Direction.DESC, "totalRevenue");
        };
        Pageable pageable = PageRequest.of(page - 1, pageSize, sorting);
        Page<EventAnalytics> eventsPage = eventAnalyticsRepository.findAll(pageable);

        List<EventComparisonResponse.EventRow> rows = eventsPage.getContent().stream()
            .map(e -> new EventComparisonResponse.EventRow(
                e.getEventId(),
                e.getTitle() != null ? e.getTitle() : "",
                e.getStartDateTime() != null ? e.getStartDateTime().toString() : "",
                e.getTotalTicketsSold() != null ? e.getTotalTicketsSold() : 0,
                e.getCapacity() != null ? e.getCapacity() : 0,
                e.getTotalRevenue() != null ? e.getTotalRevenue().doubleValue() : 0,
                null
            ))
            .toList();

        return new EventComparisonResponse(rows, page, eventsPage.getTotalPages());
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
            e.getCapacity() != null ? e.getCapacity() : 0,
            new EventSummaryResponse.EventKpis(
                new EventSummaryResponse.RevenueKpi(revenue, 0),
                new EventSummaryResponse.SoldKpi(ticketsSold, 0),
                new EventSummaryResponse.CheckInKpi(null, null),
                new EventSummaryResponse.RefundKpi(null, null)
            )
        );
    }

    @Override
    public EventSalesTimelineResponse getEventSalesTimeline(String eventId, String granularity) {
        List<SalesRecord> records = salesRecordRepository.findByEventIdOrderBySaleDateAsc(eventId);

        List<EventSalesTimelineResponse.PeriodDataPoint> series = records.stream()
            .map(r -> new EventSalesTimelineResponse.PeriodDataPoint(
                r.getSaleDate().toString(),
                r.getTicketsSold() != null ? r.getTicketsSold() : 0))
            .toList();

        return new EventSalesTimelineResponse(granularity, series);
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