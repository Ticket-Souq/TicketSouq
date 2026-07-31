package org.ticketsouq.analyticsservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.ticketsouq.analyticsservice.dto.*;
import org.ticketsouq.analyticsservice.service.AnalyticsService;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Org-wide and single-event analytics dashboards.")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/overview/kpis")
    public ResponseEntity<OverviewKpiResponse> getOverviewKpis(
        @RequestHeader("X-User-Id") String userId,
        @RequestParam(defaultValue = "30d") String range) {
        return ResponseEntity.ok(analyticsService.getOverviewKpis(userId, range));
    }

    @GetMapping("/overview/sales-pace")
    public ResponseEntity<SalesPaceResponse> getSalesPace(
        @RequestHeader("X-User-Id") String userId,
        @RequestParam(defaultValue = "30d") String range,
        @RequestParam Optional<String> eventId) {
        return ResponseEntity.ok(analyticsService.getSalesPace(userId, range, eventId));
    }

    @GetMapping("/overview/events")
    public ResponseEntity<EventComparisonResponse> getEvents(
        @RequestHeader("X-User-Id") String userId,
        @RequestParam(defaultValue = "30d") String range,
        @RequestParam(defaultValue = "revenue") String sort,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(analyticsService.getEventComparison(userId, range, sort, page, pageSize));
    }

    @GetMapping("/events/{eventId}/summary")
    public ResponseEntity<EventSummaryResponse> getEventSummary(
        @RequestHeader("X-User-Id") String userId,
        @PathVariable String eventId) {
        return ResponseEntity.ok(analyticsService.getEventSummary(eventId));
    }

    @GetMapping("/events/{eventId}/sales-timeline")
    public ResponseEntity<EventSalesTimelineResponse> getSalesTimeline(
        @RequestHeader("X-User-Id") String userId,
        @PathVariable String eventId,
        @RequestParam(defaultValue = "day") String granularity) {
        return ResponseEntity.ok(analyticsService.getEventSalesTimeline(eventId, granularity));
    }
}
