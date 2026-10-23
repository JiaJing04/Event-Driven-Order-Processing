package com.example.saga.payment.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

/** Deterministic fake gateway so failure paths can be exercised on demand. */
@Component
public class SimulatedPaymentGateway implements PaymentGateway {

    private final PaymentProperties properties;

    public SimulatedPaymentGateway(PaymentProperties properties) {
        this.properties = properties;
    }

    @Override
    public Result charge(UUID orderId, String customerId, BigDecimal amount) {
        if (customerId != null && customerId.toUpperCase(Locale.ROOT).contains("DECLINE")) {
            return Result.decline("Card declined by issuer");
        }
        if (amount.compareTo(properties.maxAmount()) > 0) {
            return Result.decline("Amount " + amount + " exceeds limit " + properties.maxAmount());
        }
        return Result.approve();
    }
}
