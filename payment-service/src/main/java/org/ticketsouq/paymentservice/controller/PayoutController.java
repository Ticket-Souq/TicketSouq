package org.ticketsouq.paymentservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.ticketsouq.paymentservice.dto.BulkPayResult;
import org.ticketsouq.paymentservice.dto.OrgPayoutSummary;
import org.ticketsouq.paymentservice.dto.PayoutDashboardResponse;
import org.ticketsouq.paymentservice.dto.PayoutResponse;
import org.ticketsouq.paymentservice.service.PayoutService;
import org.ticketsouq.sharedmodule.GeneralExceptions.ResourceNotFoundException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/v1/payout")
@RequiredArgsConstructor
public class PayoutController {

    private final PayoutService payoutService;

    @GetMapping("/{payoutId}")
    public ResponseEntity<PayoutResponse> getPayout(@PathVariable UUID payoutId) {
        return ResponseEntity.ok(payoutService.getPayoutById(payoutId));
    }

    @GetMapping("/event/{eventId}")
    public ResponseEntity<PayoutResponse> getByEventId(@PathVariable UUID eventId) {
        return ResponseEntity.ok(payoutService.getPayoutByEventId(eventId));
    }

    @GetMapping("/organizer/{organizerId}")
    public ResponseEntity<List<PayoutResponse>> getByOrganizer(@PathVariable UUID organizerId) {
        return ResponseEntity.ok(payoutService.getPayoutsByOrganizerId(organizerId));
    }

    @GetMapping
    public ResponseEntity<List<PayoutResponse>> getAll(
            @RequestParam(required = false) String organization,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(payoutService.getAllPayouts(organization, status));
    }

    @PostMapping("/{payoutId}/retry")
    public ResponseEntity<PayoutResponse> retryPayout(@PathVariable UUID payoutId) {
        payoutService.retryPayout(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout", payoutId));
        return ResponseEntity.ok(payoutService.getPayoutById(payoutId));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<PayoutDashboardResponse> getDashboard(
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(payoutService.getDashboard(search));
    }

    @GetMapping("/organization/{organization}/summary")
    public ResponseEntity<OrgPayoutSummary> getOrgSummary(@PathVariable String organization) {
        return ResponseEntity.ok(payoutService.getOrgSummary(organization));
    }

    @GetMapping("/my-organization/summary")
    public ResponseEntity<OrgPayoutSummary> getMyOrgSummary(@RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(payoutService.getMyOrgSummary(userId));
    }

    @GetMapping("/records")
    public ResponseEntity<Page<PayoutResponse>> getRecords(
            @RequestParam(required = false) String organization,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(payoutService.getPayoutRecords(organization, status, pageable));
    }

    @PostMapping("/pay/organization/{organization}")
    public ResponseEntity<BulkPayResult> payOrganization(@PathVariable String organization) {
        return ResponseEntity.ok(payoutService.payForOrganization(organization));
    }

    @PostMapping("/pay/event/{eventId}")
    public ResponseEntity<PayoutResponse> payEvent(
            @PathVariable UUID eventId,
            @RequestParam(required = false) String organization) {
        return ResponseEntity.ok(payoutService.payForEvent(eventId, organization));
    }
}
