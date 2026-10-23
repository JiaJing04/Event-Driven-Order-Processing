package com.example.saga.order;

import com.example.saga.order.domain.Order;
import com.example.saga.order.domain.OrderLine;
import com.example.saga.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderTest {

    private static Order newOrder() {
        return Order.create(UUID.randomUUID(), "customer-1", List.of(
                new OrderLine("A", 2, new BigDecimal("10.50")),
                new OrderLine("B", 1, new BigDecimal("4.00"))));
    }

    @Test
    void newOrderIsPendingWithComputedTotal() {
        Order order = newOrder();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("25.00");
    }

    @Test
    void confirmOnlyWorksFromPending() {
        Order order = newOrder();
        assertThat(order.confirm()).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        assertThat(order.confirm()).isFalse();
        assertThat(order.cancel("late failure")).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getFailureReason()).isNull();
    }

    @Test
    void cancelRecordsReasonAndIsTerminal() {
        Order order = newOrder();
        assertThat(order.cancel("no stock")).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getFailureReason()).isEqualTo("no stock");

        assertThat(order.confirm()).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }
}
