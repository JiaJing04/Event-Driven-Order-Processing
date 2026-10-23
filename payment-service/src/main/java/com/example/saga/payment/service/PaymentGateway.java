package com.example.saga.payment.service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Port to the external payment provider. A real implementation would call the provider with
 * {@code orderId} as the provider-side idempotency key, so that a retry after a crash cannot charge twice.
 */
public interface PaymentGateway {

    Result charge(UUID orderId, String customerId, BigDecimal amount);

    record Result(boolean approved, String declineReason) {
        public static Result approve() {
            return new Result(true, null);
        }

        public static Result decline(String reason) {
            return new Result(false, reason);
        }
    }
}
