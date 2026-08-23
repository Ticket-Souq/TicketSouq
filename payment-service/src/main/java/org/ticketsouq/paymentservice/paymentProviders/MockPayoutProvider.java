package org.ticketsouq.paymentservice.paymentProviders;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ticketsouq.paymentservice.dto.PayoutResult;
import org.ticketsouq.paymentservice.model.Payout;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@RequiredArgsConstructor
public class MockPayoutProvider implements PayoutProvider {

    private final int successRate;

    @Override
    public PayoutResult payout(Payout payout) {
        int rate = Math.max(0, Math.min(100, successRate));
        boolean success = ThreadLocalRandom.current().nextInt(100) < rate;

        if (success) {
            String mockId = "mock_tr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
            log.info("Mock payout SUCCESS for payoutId={}, eventId={}, amount={}, mockId={}",
                    payout.getId(), payout.getEventId(), payout.getNetAmount(), mockId);
            return PayoutResult.success(mockId);
        } else {
            log.warn("Mock payout FAILED for payoutId={}, eventId={}", payout.getId(), payout.getEventId());
            return PayoutResult.failed("Mock payout failure (successRate=" + rate + "%)");
        }
    }
}
