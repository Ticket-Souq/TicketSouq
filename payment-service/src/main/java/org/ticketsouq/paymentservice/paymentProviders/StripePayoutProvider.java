package org.ticketsouq.paymentservice.paymentProviders;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ticketsouq.paymentservice.dto.PayoutResult;
import org.ticketsouq.paymentservice.model.Payout;

/**
 * Placeholder for real Stripe Connect Transfers.
 * When payout.provider=stripe and Stripe Connect is configured, replace the mock logic
 * with Transfer.create(TransferCreateParams.builder()
 *   .setAmount(convertToCents(payout.getNetAmount()))
 *   .setCurrency(payout.getCurrency().toLowerCase())
 *   .setDestination(organizationStripeAccountId)
 *   .setTransferGroup(payout.getEventId().toString())
 *   .build(), RequestOptions.builder().setIdempotencyKey(payout.getId().toString()).build())
 *
 * For now this behaves as a mock to keep the build green without Connect onboarding.
 */
@Slf4j
@RequiredArgsConstructor
public class StripePayoutProvider implements PayoutProvider {

    @Override
    public PayoutResult payout(Payout payout) {
        // In a real Stripe Connect setup, uncomment Transfer logic above.
        // For mock-mode parity, generate a fake Stripe transfer id.
        String fakeId = "tr_mock_stripe_" + payout.getId().toString().substring(0, 8);
        log.info("StripePayoutProvider (mocked) SUCCESS for payoutId={}, eventId={}, fakeId={}",
                payout.getId(), payout.getEventId(), fakeId);
        return PayoutResult.success(fakeId);
    }
}
