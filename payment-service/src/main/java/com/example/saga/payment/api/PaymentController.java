package com.example.saga.payment.api;

import com.example.saga.payment.domain.Payment;
import com.example.saga.payment.domain.PaymentRepository;
import com.example.saga.payment.domain.PaymentStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    public record PaymentResponse(UUID id, UUID orderId, String customerId, BigDecimal amount,
                                  PaymentStatus status, String failureReason, Instant createdAt) {
        static PaymentResponse from(Payment p) {
            return new PaymentResponse(p.getId(), p.getOrderId(), p.getCustomerId(), p.getAmount(),
                    p.getStatus(), p.getFailureReason(), p.getCreatedAt());
        }
    }

    private final PaymentRepository payments;

    public PaymentController(PaymentRepository payments) {
        this.payments = payments;
    }

    @GetMapping("/by-order/{orderId}")
    public ResponseEntity<PaymentResponse> byOrder(@PathVariable UUID orderId) {
        return payments.findByOrderId(orderId)
                .map(p -> ResponseEntity.ok(PaymentResponse.from(p)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
