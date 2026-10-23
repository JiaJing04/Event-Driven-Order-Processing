package com.example.saga.payment;

import com.example.saga.payment.service.PaymentGateway;
import com.example.saga.payment.service.PaymentProperties;
import com.example.saga.payment.service.SimulatedPaymentGateway;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatedPaymentGatewayTest {

    private final SimulatedPaymentGateway gateway =
            new SimulatedPaymentGateway(new PaymentProperties(new BigDecimal("1000")));

    @Test
    void approvesAmountsWithinLimit() {
        PaymentGateway.Result result = gateway.charge(UUID.randomUUID(), "alice", new BigDecimal("1000.00"));
        assertThat(result.approved()).isTrue();
    }

    @Test
    void declinesAmountsAboveLimit() {
        PaymentGateway.Result result = gateway.charge(UUID.randomUUID(), "alice", new BigDecimal("1000.01"));
        assertThat(result.approved()).isFalse();
        assertThat(result.declineReason()).contains("exceeds limit");
    }

    @Test
    void declinesMarkedCustomersRegardlessOfAmount() {
        PaymentGateway.Result result = gateway.charge(UUID.randomUUID(), "bob-decline-me", new BigDecimal("1.00"));
        assertThat(result.approved()).isFalse();
    }
}
