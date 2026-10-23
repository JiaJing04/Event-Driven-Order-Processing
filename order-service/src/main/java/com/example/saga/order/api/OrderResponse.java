package com.example.saga.order.api;

import com.example.saga.order.domain.Order;
import com.example.saga.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String customerId,
        OrderStatus status,
        BigDecimal totalAmount,
        String failureReason,
        Instant createdAt,
        Instant updatedAt,
        List<Item> items) {

    public record Item(String sku, int quantity, BigDecimal unitPrice) {
    }

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getFailureReason(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getItems().stream()
                        .map(l -> new Item(l.getSku(), l.getQuantity(), l.getUnitPrice()))
                        .toList());
    }
}
