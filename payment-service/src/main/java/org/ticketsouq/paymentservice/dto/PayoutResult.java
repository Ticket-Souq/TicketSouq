package org.ticketsouq.paymentservice.dto;

public record PayoutResult(
        String transferId,
        String status,
        String failureReason
) {
    public static PayoutResult success(String transferId) {
        return new PayoutResult(transferId, "COMPLETED", null);
    }

    public static PayoutResult failed(String reason) {
        return new PayoutResult(null, "FAILED", reason);
    }
}
