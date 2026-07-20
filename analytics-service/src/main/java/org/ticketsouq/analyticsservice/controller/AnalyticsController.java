package org.ticketsouq.analyticsservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.ticketsouq.analyticsservice.dto.*;
import org.ticketsouq.analyticsservice.service.AnalyticsService;

import java.util.Optional;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Org-wide and single-event analytics dashboards.")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/overview/kpis")
    public ResponseEntity<OverviewKpiResponse> getOverviewKpis(
        @RequestParam(defaultValue = "30d") String range) {
        return ResponseEntity.ok(analyticsService.getOverviewKpis(range));
    }

    @GetMapping("/overview/sales-pace")
    public ResponseEntity<SalesPaceResponse> getSalesPace(
        @RequestParam(defaultValue = "30d") String range,
        @RequestParam Optional<String> eventId) {
        return ResponseEntity.ok(analyticsService.getSalesPace(range, eventId));
    }

    @GetMapping("/overview/revenue-by-tier")
    public ResponseEntity<RevenueByTierResponse> getRevenueByTier(
        @RequestParam(defaultValue = "30d") String range,
        @RequestParam Optional<String> eventId) {
        return ResponseEntity.ok(analyticsService.getRevenueByTier(range, eventId));
    }

    @GetMapping("/overview/sales-by-channel")
    public ResponseEntity<SalesByChannelResponse> getSalesByChannel(
        @RequestParam(defaultValue = "30d") String range,
        @RequestParam Optional<String> eventId) {
        return ResponseEntity.ok(analyticsService.getSalesByChannel(range, eventId));
    }

    @GetMapping("/overview/events")
    public ResponseEntity<EventComparisonResponse> getEvents(
        @RequestParam(defaultValue = "30d") String range,
        @RequestParam(defaultValue = "revenue") String sort,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(analyticsService.getEventComparison(range, sort, page, pageSize));
    }

    @GetMapping("/overview/no-shows-by-tier")
    public ResponseEntity<NoShowsByTierResponse> getNoShowsByTier(
        @RequestParam(defaultValue = "30d") String range) {
        return ResponseEntity.ok(analyticsService.getNoShowsByTier(range));
    }

    @GetMapping("/events/{eventId}/summary")
    public ResponseEntity<EventSummaryResponse> getEventSummary(
        @PathVariable String eventId) {
        return ResponseEntity.ok(analyticsService.getEventSummary(eventId));
    }

    @GetMapping("/events/{eventId}/sales-timeline")
    public ResponseEntity<EventSalesTimelineResponse> getSalesTimeline(
        @PathVariable String eventId,
        @RequestParam(defaultValue = "day") String granularity) {
        return ResponseEntity.ok(analyticsService.getEventSalesTimeline(eventId, granularity));
    }

    @GetMapping("/events/{eventId}/tiers")
    public ResponseEntity<EventTiersResponse> getEventTiers(
        @PathVariable String eventId) {
        return ResponseEntity.ok(analyticsService.getEventTiers(eventId));
    }

    @GetMapping("/events/{eventId}/channels")
    public ResponseEntity<EventChannelsResponse> getEventChannels(
        @PathVariable String eventId) {
        return ResponseEntity.ok(analyticsService.getEventChannels(eventId));
    }

    @GetMapping("/events/{eventId}/check-in-curve")
    public ResponseEntity<CheckInCurveResponse> getCheckInCurve(
        @PathVariable String eventId) {
        return ResponseEntity.ok(analyticsService.getCheckInCurve(eventId));
    }

    @GetMapping("/events/{eventId}/demographics")
    public ResponseEntity<DemographicsResponse> getDemographics(
        @PathVariable String eventId) {
        return ResponseEntity.ok(analyticsService.getDemographics(eventId));
    }

    @GetMapping("/events/{eventId}/refunds")
    public ResponseEntity<RefundsResponse> getRefunds(
        @PathVariable String eventId,
        @RequestParam(defaultValue = "day") String granularity) {
        return ResponseEntity.ok(analyticsService.getRefunds(eventId, granularity));
    }

    @GetMapping("/events/{eventId}/profit")
    public ResponseEntity<ProfitResponse> getProfit(
        @PathVariable String eventId) {
        return ResponseEntity.ok(analyticsService.getProfit(eventId));
    }
}
