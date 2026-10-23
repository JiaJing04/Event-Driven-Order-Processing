package com.example.saga.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    private Long version;

    protected Payment() {
    }

    public static Payment completed(UUID id, UUID orderId, String customerId, BigDecimal amount) {
        return create(id, orderId, customerId, amount, PaymentStatus.COMPLETED, null);
    }

    public static Payment failed(UUID id, UUID orderId, String customerId, BigDecimal amount, String reason) {
        return create(id, orderId, customerId, amount, PaymentStatus.FAILED, reason);
    }

    private static Payment create(UUID id, UUID orderId, String customerId, BigDecimal amount,
                                  PaymentStatus status, String reason) {
        Payment p = new Payment();
        p.id = id;
        p.orderId = orderId;
        p.customerId = customerId;
        p.amount = amount;
        p.status = status;
        p.failureReason = reason;
        p.createdAt = Instant.now();
        return p;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
