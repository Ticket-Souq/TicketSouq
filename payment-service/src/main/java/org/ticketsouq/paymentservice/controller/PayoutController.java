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
import org.ticketsouq.paymentservice.model.Payout;
import org.ticketsouq.paymentservice.repository.PayoutRepository;
import org.ticketsouq.paymentservice.service.PayoutService;
import org.ticketsouq.sharedmodule.GeneralExceptions.ResourceNotFoundException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/v1/payout")
@RequiredArgsConstructor
public class PayoutController {

    private final PayoutRepository payoutRepository;
    private final PayoutService payoutService;

    @GetMapping("/{payoutId}")
    public ResponseEntity<PayoutResponse> getPayout(@PathVariable UUID payoutId) {
        Payout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout", payoutId));
        return ResponseEntity.ok(toResponse(payout));
    }

    @GetMapping("/event/{eventId}")
    public ResponseEntity<PayoutResponse> getByEventId(@PathVariable UUID eventId) {
        Payout payout = payoutRepository.findByEventId(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout for event", eventId));
        return ResponseEntity.ok(toResponse(payout));
    }

    @GetMapping("/organizer/{organizerId}")
    public ResponseEntity<List<PayoutResponse>> getByOrganizer(@PathVariable UUID organizerId) {
        List<PayoutResponse> list = payoutRepository.findByOrganizerId(organizerId).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(list);
    }

    @GetMapping
    public ResponseEntity<List<PayoutResponse>> getAll(
            @RequestParam(required = false) String organization,
            @RequestParam(required = false) String status) {
        List<Payout> payouts = payoutRepository.findAll();
        List<PayoutResponse> filtered = payouts.stream()
                .filter(p -> organization == null || organization.equals(p.getOrganization()))
                .filter(p -> status == null || status.equalsIgnoreCase(p.getStatus()))
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(filtered);
    }

    @PostMapping("/{payoutId}/retry")
    public ResponseEntity<PayoutResponse> retryPayout(@PathVariable UUID payoutId) {
        Payout payout = payoutService.retryPayout(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout", payoutId));
        return ResponseEntity.ok(toResponse(payout));
    }

    // ─── Dashboard & Bulk Pay ───────────────────────────────────────────

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
        Page<Payout> p;
        if (organization != null && status != null) {
            p = payoutRepository.findByOrganizationAndStatus(organization, status, pageable);
        } else if (organization != null) {
            p = payoutRepository.findByOrganization(organization, pageable);
        } else if (status != null) {
            p = payoutRepository.findByStatus(status, pageable);
        } else {
            p = payoutRepository.findAll(pageable);
        }
        return ResponseEntity.ok(p.map(this::toResponse));
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

    private PayoutResponse toResponse(Payout p) {
        return new PayoutResponse(
                p.getId(),
                p.getEventId(),
                p.getOrganizerId(),
                p.getOrganization(),
                p.getAmount(),
                p.getNetAmount(),
                p.getCurrency(),
                p.getStatus(),
                p.getProviderTransferId(),
                p.getStripeTransferId(),
                p.getFailureReason(),
                p.getRetryCount(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
