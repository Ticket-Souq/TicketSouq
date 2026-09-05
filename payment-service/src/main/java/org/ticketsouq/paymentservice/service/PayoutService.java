package org.ticketsouq.paymentservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.paymentservice.dto.PayoutResult;
import org.ticketsouq.paymentservice.enums.PaymentStatus;
import org.ticketsouq.paymentservice.model.Payout;
import org.ticketsouq.paymentservice.paymentProviders.PayoutProvider;
import org.ticketsouq.paymentservice.repository.PayoutRepository;
import org.ticketsouq.paymentservice.repository.PaymentRepository;
import org.ticketsouq.sharedmodule.EventService.events.EventPayoutReleaseEvent;

import org.ticketsouq.paymentservice.client.UserServiceClient;
import org.ticketsouq.paymentservice.dto.BulkPayResult;
import org.ticketsouq.paymentservice.dto.EventPayoutRow;
import org.ticketsouq.paymentservice.dto.OrgPayoutSummary;
import org.ticketsouq.paymentservice.dto.PayoutDashboardResponse;
import org.ticketsouq.paymentservice.dto.PayoutResponse;
import org.ticketsouq.sharedmodule.GeneralExceptions.BusinessException;
import org.ticketsouq.sharedmodule.GeneralExceptions.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutService {

    private final PayoutRepository payoutRepository;
    private final PaymentRepository paymentRepository;
    private final PayoutProvider payoutProvider;
    private final UserServiceClient userServiceClient;

    @Transactional
    public Optional<Payout> handlePayoutRelease(EventPayoutReleaseEvent event) {
        UUID eventId = event.eventId();
        String organization = event.organization();

        log.info("Received EventPayoutReleaseEvent eventId={}, organization={}", eventId, organization);

        // Idempotency: one payout per event
        Optional<Payout> existing = payoutRepository.findByEventId(eventId);
        if (existing.isPresent()) {
            log.info("Payout already exists for eventId={}, payoutId={}, status={}", eventId, existing.get().getId(), existing.get().getStatus());
            return existing;
        }

        // Aggregate gross amount: sum of SUCCESS payments for this event (0% fee => net = gross)
        BigDecimal gross = paymentRepository.sumAmountByEventIdAndStatus(eventId, PaymentStatus.SUCCESS);
        if (gross == null) {
            gross = BigDecimal.ZERO;
        }

        log.info("Aggregated gross amount for eventId={}: {}", eventId, gross);

        Payout payout = Payout.builder()
                .eventId(eventId)
                .organization(organization)
                .organizerId(null) // resolved lazily; nullable for mock
                .amount(gross)
                .netAmount(gross)
                .currency("EGP")
                .status("PENDING")
                .retryCount(0)
                .build();

        try {
            payoutRepository.save(payout);
        } catch (DataIntegrityViolationException e) {
            log.warn("Race condition creating payout for eventId={}, fetching existing", eventId);
            return payoutRepository.findByEventId(eventId);
        }

        // Mock transfer
        PayoutResult result;
        try {
            result = payoutProvider.payout(payout);
        } catch (Exception e) {
            log.error("Payout provider threw exception for payoutId={}, eventId={}: {}", payout.getId(), eventId, e.getMessage(), e);
            payout.setStatus("FAILED");
            payout.setFailureReason(e.getMessage());
            payout.setRetryCount((payout.getRetryCount() == null ? 0 : payout.getRetryCount()) + 1);
            payoutRepository.save(payout);
            return Optional.of(payout);
        }

        if ("COMPLETED".equals(result.status())) {
            payout.setStatus("COMPLETED");
            payout.setProviderTransferId(result.transferId());
            payout.setStripeTransferId(result.transferId()); // back-compat
            payout.setFailureReason(null);
            log.info("Payout COMPLETED for eventId={}, payoutId={}, transferId={}, amount={}", eventId, payout.getId(), result.transferId(), gross);
        } else {
            payout.setStatus("FAILED");
            payout.setFailureReason(result.failureReason());
            payout.setRetryCount((payout.getRetryCount() == null ? 0 : payout.getRetryCount()) + 1);
            log.warn("Payout FAILED for eventId={}, payoutId={}, reason={}", eventId, payout.getId(), result.failureReason());
        }

        payoutRepository.save(payout);
        return Optional.of(payout);
    }

    @Transactional(readOnly = true)
    public Optional<Payout> getByEventId(UUID eventId) {
        return payoutRepository.findByEventId(eventId);
    }

    @Transactional(readOnly = true)
    public Optional<Payout> getById(UUID payoutId) {
        return payoutRepository.findById(payoutId);
    }

    @Transactional(readOnly = true)
    public PayoutResponse getPayoutById(UUID payoutId) {
        Payout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout", payoutId));
        return toPayoutResponse(payout);
    }

    @Transactional(readOnly = true)
    public PayoutResponse getPayoutByEventId(UUID eventId) {
        Payout payout = payoutRepository.findByEventId(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout for event", eventId));
        return toPayoutResponse(payout);
    }

    @Transactional(readOnly = true)
    public List<PayoutResponse> getPayoutsByOrganizerId(UUID organizerId) {
        return payoutRepository.findByOrganizerId(organizerId).stream()
                .map(this::toPayoutResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PayoutResponse> getAllPayouts(String organization, String status) {
        List<Payout> payouts;
        if (organization != null && status != null) {
            payouts = payoutRepository.findByOrganizationAndStatus(organization, status);
        } else if (organization != null) {
            payouts = payoutRepository.findByOrganization(organization);
        } else if (status != null) {
            payouts = payoutRepository.findByStatus(status);
        } else {
            payouts = payoutRepository.findAll();
        }
        return payouts.stream()
                .map(this::toPayoutResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<PayoutResponse> getPayoutRecords(String organization, String status, Pageable pageable) {
        Page<Payout> page;
        if (organization != null && status != null) {
            page = payoutRepository.findByOrganizationAndStatus(organization, status, pageable);
        } else if (organization != null) {
            page = payoutRepository.findByOrganization(organization, pageable);
        } else if (status != null) {
            page = payoutRepository.findByStatus(status, pageable);
        } else {
            page = payoutRepository.findAll(pageable);
        }
        return page.map(this::toPayoutResponse);
    }

    @Transactional
    public Optional<Payout> retryPayout(UUID payoutId) {
        Payout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new IllegalArgumentException("Payout not found: " + payoutId));

        if ("COMPLETED".equals(payout.getStatus())) {
            log.info("Payout {} already COMPLETED, skipping retry", payoutId);
            return Optional.of(payout);
        }

        log.info("Retrying payoutId={}, eventId={}, attempt {}", payoutId, payout.getEventId(), (payout.getRetryCount() == null ? 0 : payout.getRetryCount()) + 1);
        PayoutResult result = payoutProvider.payout(payout);
        if ("COMPLETED".equals(result.status())) {
            payout.setStatus("COMPLETED");
            payout.setProviderTransferId(result.transferId());
            payout.setStripeTransferId(result.transferId());
            payout.setFailureReason(null);
        } else {
            payout.setStatus("FAILED");
            payout.setFailureReason(result.failureReason());
        }
        payout.setRetryCount((payout.getRetryCount() == null ? 0 : payout.getRetryCount()) + 1);
        payoutRepository.save(payout);
        return Optional.of(payout);
    }

    @Transactional(readOnly = true)
    public PayoutDashboardResponse getDashboard(String search) {
        List<String> organizations;
        if (search != null && !search.isBlank()) {
            organizations = payoutRepository.findDistinctOrganizationsBySearch(search.trim());
        } else {
            organizations = payoutRepository.findDistinctOrganizations();
        }

        List<OrgPayoutSummary> summaries = new ArrayList<>();
        BigDecimal totalOwed = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;

        for (String org : organizations) {
            OrgPayoutSummary summary = buildOrgSummary(org);
            summaries.add(summary);
            totalOwed = totalOwed.add(summary.owed());
            totalPaid = totalPaid.add(summary.paid());
        }

        BigDecimal totalOutstanding = totalOwed.subtract(totalPaid);
        if (totalOutstanding.compareTo(BigDecimal.ZERO) < 0) {
            totalOutstanding = BigDecimal.ZERO;
        }

        return new PayoutDashboardResponse(totalOwed, totalPaid, totalOutstanding, summaries);
    }

    @Transactional(readOnly = true)
    public OrgPayoutSummary getOrgSummary(String organization) {
        return buildOrgSummary(organization);
    }

    @Transactional(readOnly = true)
    public OrgPayoutSummary getMyOrgSummary(UUID userId) {
        String organization = userServiceClient.getOrganizationNameByUserId(userId);
        if (organization == null || organization.isBlank()) {
            throw new BusinessException("Organization not found for user: " + userId, HttpStatus.NOT_FOUND);
        }
        return buildOrgSummary(organization);
    }

    private OrgPayoutSummary buildOrgSummary(String org) {
        List<UUID> eventIds = payoutRepository.findDistinctEventIdsByOrganization(org);

        // owed per event from payment_model
        Map<UUID, BigDecimal> owedByEvent = new HashMap<>();
        if (!eventIds.isEmpty()) {
            List<Object[]> rows = paymentRepository.sumOwedGroupedByEventIds(eventIds, PaymentStatus.SUCCESS);
            for (Object[] r : rows) {
                UUID eid = (UUID) r[0];
                BigDecimal total = (BigDecimal) r[1];
                owedByEvent.put(eid, total != null ? total : BigDecimal.ZERO);
            }
        }

        BigDecimal orgOwed = BigDecimal.ZERO;
        BigDecimal orgPaid = payoutRepository.sumPaidByOrganization(org);
        if (orgPaid == null) orgPaid = BigDecimal.ZERO;

        List<EventPayoutRow> eventRows = new ArrayList<>();
        int payoutCount = 0;

        for (UUID eventId : eventIds) {
            BigDecimal owed = owedByEvent.getOrDefault(eventId, BigDecimal.ZERO);
            orgOwed = orgOwed.add(owed);

            Optional<Payout> payoutOpt = payoutRepository.findByEventId(eventId);
            PayoutResponse payoutResp = null;
            BigDecimal paidForEvent = BigDecimal.ZERO;
            if (payoutOpt.isPresent()) {
                Payout p = payoutOpt.get();
                payoutResp = toPayoutResponse(p);
                payoutCount++;
                if ("COMPLETED".equals(p.getStatus()) && p.getNetAmount() != null) {
                    paidForEvent = p.getNetAmount();
                }
            }

            eventRows.add(new EventPayoutRow(eventId, owed, paidForEvent, payoutResp));
        }

        // Also count owed for events that have payments but no payout row yet? payout table is source of org→events,
        // so events without payout row are not in dashboard. That's intentional per simplification.

        BigDecimal outstanding = orgOwed.subtract(orgPaid);
        if (outstanding.compareTo(BigDecimal.ZERO) < 0) outstanding = BigDecimal.ZERO;

        return new OrgPayoutSummary(org, orgOwed, orgPaid, outstanding, eventIds.size(), payoutCount, eventRows);
    }

    @Transactional
    public BulkPayResult payForOrganization(String org) {
        List<UUID> eventIds = payoutRepository.findDistinctEventIdsByOrganization(org);
        if (eventIds.isEmpty()) {
            log.info("No events found for organization {} to pay", org);
            return new BulkPayResult(0, 0, 0, List.of());
        }

        int created = 0;
        int skipped = 0;
        int failed = 0;
        List<PayoutResponse> results = new ArrayList<>();

        for (UUID eventId : eventIds) {
            Optional<Payout> existing = payoutRepository.findByEventId(eventId);
            if (existing.isPresent()) {
                Payout p = existing.get();
                if ("COMPLETED".equals(p.getStatus())) {
                    skipped++;
                    results.add(toPayoutResponse(p));
                    continue;
                }
                // retry FAILED/PENDING
                Optional<Payout> retried = retryPayout(p.getId());
                if (retried.isPresent()) {
                    Payout rp = retried.get();
                    results.add(toPayoutResponse(rp));
                    if ("COMPLETED".equals(rp.getStatus())) created++;
                    else failed++;
                } else {
                    failed++;
                }
            } else {
                // No payout row yet — create via handlePayoutRelease logic using org
                // We need org mapping; use passed org
                BigDecimal gross = paymentRepository.sumAmountByEventIdAndStatus(eventId, PaymentStatus.SUCCESS);
                if (gross == null) gross = BigDecimal.ZERO;

                Payout newPayout = Payout.builder()
                        .eventId(eventId)
                        .organization(org)
                        .organizerId(null)
                        .amount(gross)
                        .netAmount(gross)
                        .currency("EGP")
                        .status("PENDING")
                        .retryCount(0)
                        .build();
                try {
                    payoutRepository.save(newPayout);
                } catch (DataIntegrityViolationException e) {
                    // race
                    Optional<Payout> raced = payoutRepository.findByEventId(eventId);
                    if (raced.isPresent()) {
                        Payout rp = raced.get();
                        results.add(toPayoutResponse(rp));
                        if ("COMPLETED".equals(rp.getStatus())) skipped++;
                        else {
                            // retry
                            Optional<Payout> retried = retryPayout(rp.getId());
                            if (retried.isPresent() && "COMPLETED".equals(retried.get().getStatus())) created++;
                            else failed++;
                        }
                    }
                    continue;
                }

                PayoutResult r = payoutProvider.payout(newPayout);
                if ("COMPLETED".equals(r.status())) {
                    newPayout.setStatus("COMPLETED");
                    newPayout.setProviderTransferId(r.transferId());
                    newPayout.setStripeTransferId(r.transferId());
                    created++;
                } else {
                    newPayout.setStatus("FAILED");
                    newPayout.setFailureReason(r.failureReason());
                    newPayout.setRetryCount(1);
                    failed++;
                }
                payoutRepository.save(newPayout);
                results.add(toPayoutResponse(newPayout));
            }
        }

        return new BulkPayResult(created, skipped, failed, results);
    }

    @Transactional
    public PayoutResponse payForEvent(UUID eventId, String organizationHint) {
        Optional<Payout> existing = payoutRepository.findByEventId(eventId);
        if (existing.isPresent()) {
            Payout p = existing.get();
            if ("COMPLETED".equals(p.getStatus())) {
                log.info("Payout for eventId={} already COMPLETED", eventId);
                return toPayoutResponse(p);
            }
            // retry
            Optional<Payout> retried = retryPayout(p.getId());
            return retried.map(this::toPayoutResponse)
                    .orElseThrow(() -> new IllegalStateException("Retry failed"));
        }

        // No payout yet — need organization to create
        String org = organizationHint;
        if (org == null || org.isBlank()) {
            // try to infer from any payout with same org? fallback to "unknown"
            org = "unknown";
            log.warn("Creating payout for eventId={} without organization hint, using 'unknown'", eventId);
        }

        BigDecimal gross = paymentRepository.sumAmountByEventIdAndStatus(eventId, PaymentStatus.SUCCESS);
        if (gross == null) gross = BigDecimal.ZERO;

        Payout newPayout = Payout.builder()
                .eventId(eventId)
                .organization(org)
                .organizerId(null)
                .amount(gross)
                .netAmount(gross)
                .currency("EGP")
                .status("PENDING")
                .retryCount(0)
                .build();
        payoutRepository.save(newPayout);

        PayoutResult r = payoutProvider.payout(newPayout);
        if ("COMPLETED".equals(r.status())) {
            newPayout.setStatus("COMPLETED");
            newPayout.setProviderTransferId(r.transferId());
            newPayout.setStripeTransferId(r.transferId());
        } else {
            newPayout.setStatus("FAILED");
            newPayout.setFailureReason(r.failureReason());
            newPayout.setRetryCount(1);
        }
        payoutRepository.save(newPayout);
        return toPayoutResponse(newPayout);
    }

    private PayoutResponse toPayoutResponse(Payout p) {
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
