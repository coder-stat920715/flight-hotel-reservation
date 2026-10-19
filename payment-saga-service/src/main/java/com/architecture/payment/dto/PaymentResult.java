package com.architecture.payment.dto;

public record PaymentResult(String status, String reference, String reason) {

    public static PaymentResult approved(String reference) {
        return new PaymentResult("APPROVED", reference, null);
    }

    public static PaymentResult declined(String reason) {
        return new PaymentResult("DECLINED", null, reason);
    }

    /** Returned by the circuit-breaker fallback: gateway unavailable, outcome unknown. */
    public static PaymentResult deferred(String reason) {
        return new PaymentResult("DEFERRED", null, reason);
    }

    public boolean approved() {
        return "APPROVED".equals(status);
    }

    public boolean deferred() {
        return "DEFERRED".equals(status);
    }
}
