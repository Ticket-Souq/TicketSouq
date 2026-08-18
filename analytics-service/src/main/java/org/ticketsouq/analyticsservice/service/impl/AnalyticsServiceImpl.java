package org.ticketsouq.analyticsservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.ticketsouq.analyticsservice.Client.UserServiceClient;
import org.ticketsouq.analyticsservice.dto.*;
import org.ticketsouq.analyticsservice.model.EventAnalytics;
import org.ticketsouq.analyticsservice.model.SalesRecord;
import org.ticketsouq.analyticsservice.repository.EventAnalyticsRepository;
import org.ticketsouq.analyticsservice.repository.SalesRecordRepository;
import org.ticketsouq.analyticsservice.service.AnalyticsService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {

    private final EventAnalyticsRepository eventAnalyticsRepository;
    private final SalesRecordRepository salesRecordRepository;
    private final UserServiceClient userServiceClient;

    @Override
    public OverviewKpiResponse getOverviewKpis(String userId) {
        String orgName = resolveOrgName(userId);
        double revenue = eventAnalyticsRepository.sumTotalRevenueByOrgName(orgName);
        int ticketsSold = eventAnalyticsRepository.sumTotalTicketsSoldByOrgName(orgName);
        int totalCapacity = eventAnalyticsRepository.sumTotalCapacityByOrgName(orgName);

        return new OverviewKpiResponse(
            new OverviewKpiResponse.RevenueKpi(revenue, "EGP", 0),
            new OverviewKpiResponse.TicketsSoldKpi(ticketsSold, totalCapacity),
            new OverviewKpiResponse.CheckInRateKpi(null, null),
            new OverviewKpiResponse.AvgTicketPriceKpi(ticketsSold > 0 ? revenue / ticketsSold : 0, "EGP")
        );
    }

    @Override
    public SalesPaceResponse getSalesPace(String userId, Optional<String> eventId) {
        List<SalesRecord> records;
        if (eventId.isPresent()) {
            records = salesRecordRepository.findByEventIdOrderBySaleDateAsc(eventId.get());
        } else {
            records = salesRecordRepository.findByOrganizationNameOrderBySaleDateAsc(resolveOrgName(userId));
        }

        List<SalesPaceResponse.DataPoint> series = records.stream()
            .map(r -> new SalesPaceResponse.DataPoint(r.getSaleDate().toString(),
                r.getTicketsSold() != null ? r.getTicketsSold() : 0))
            .toList();

        return new SalesPaceResponse("day", series);
    }

    @Override
    public EventComparisonResponse getEventComparison(String userId, Pageable pageable) {
        Page<EventAnalytics> eventsPage = eventAnalyticsRepository.findByOrganizationName(resolveOrgName(userId), pageable);

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

        return new EventComparisonResponse(rows, eventsPage.getNumber(), eventsPage.getTotalPages());
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

    private String resolveOrgName(String userId) {
        try {
            return userServiceClient.getOrganizationNameByUserId(UUID.fromString(userId));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
