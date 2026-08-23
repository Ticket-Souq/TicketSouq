package org.ticketsouq.paymentservice.dto;

import java.util.List;

public record BulkPayResult(
        int created,
        int skipped,
        int failed,
        List<PayoutResponse> payouts
) {
}
