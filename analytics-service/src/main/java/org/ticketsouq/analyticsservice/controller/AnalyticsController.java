package org.ticketsouq.analyticsservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
    public ResponseEntity<OverviewKpiResponse> getOverviewKpis(@RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(analyticsService.getOverviewKpis(userId));
    }

    @GetMapping("/overview/sales-pace")
    public ResponseEntity<SalesPaceResponse> getSalesPace(@RequestHeader("X-User-Id") String userId, @RequestParam Optional<String> eventId) {
        return ResponseEntity.ok(analyticsService.getSalesPace(userId, eventId));
    }

    @GetMapping("/overview/events")
    public ResponseEntity<EventComparisonResponse> getEvents(@RequestHeader("X-User-Id") String userId,
        @PageableDefault(size = 20, sort = "totalRevenue", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(analyticsService.getEventComparison(userId, pageable));
    }

    @GetMapping("/events/{eventId}/summary")
    public ResponseEntity<EventSummaryResponse> getEventSummary(@PathVariable String eventId) {
        return ResponseEntity.ok(analyticsService.getEventSummary(eventId));
    }

    @GetMapping("/events/{eventId}/sales-timeline")
    public ResponseEntity<EventSalesTimelineResponse> getSalesTimeline(@PathVariable String eventId,
        @RequestParam(defaultValue = "day") String granularity) {
        return ResponseEntity.ok(analyticsService.getEventSalesTimeline(eventId, granularity));
    }
}
