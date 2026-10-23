package com.example.saga.order.service;

import com.example.saga.common.events.Events;
import com.example.saga.common.idempotency.ProcessedMessageStore;
import com.example.saga.order.domain.Order;
import com.example.saga.order.domain.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Reacts to the outcome events of the other services and moves the order to its terminal state.
 * Each method is one local transaction: de-duplication record + state change commit together.
 */
@Service
public class OrderSagaHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaHandler.class);
    static final String CONSUMER = "order-service";

    private final OrderRepository orders;
    private final ProcessedMessageStore processed;

    public OrderSagaHandler(OrderRepository orders, ProcessedMessageStore processed) {
        this.orders = orders;
        this.processed = processed;
    }

    @Transactional
    public void onPaymentCompleted(UUID eventId, Events.PaymentCompleted event) {
        if (!processed.markProcessed(CONSUMER, eventId)) {
            log.info("Duplicate PaymentCompleted {} ignored", eventId);
            return;
        }
        orders.findById(event.orderId()).ifPresentOrElse(order -> {
            if (order.confirm()) {
                log.info("Order {} CONFIRMED", order.getId());
            } else {
                log.warn("Order {} is {} - PaymentCompleted ignored", order.getId(), order.getStatus());
            }
        }, () -> log.warn("PaymentCompleted for unknown order {}", event.orderId()));
    }

    @Transactional
    public void onInventoryRejected(UUID eventId, Events.InventoryRejected event) {
        if (!processed.markProcessed(CONSUMER, eventId)) {
            log.info("Duplicate InventoryRejected {} ignored", eventId);
            return;
        }
        cancel(event.orderId(), "Inventory rejected: " + event.reason());
    }

    @Transactional
    public void onPaymentFailed(UUID eventId, Events.PaymentFailed event) {
        if (!processed.markProcessed(CONSUMER, eventId)) {
            log.info("Duplicate PaymentFailed {} ignored", eventId);
            return;
        }
        cancel(event.orderId(), "Payment failed: " + event.reason());
    }

    private void cancel(UUID orderId, String reason) {
        orders.findById(orderId).ifPresentOrElse(order -> logCancel(order, reason),
                () -> log.warn("Cancellation for unknown order {}", orderId));
    }

    private void logCancel(Order order, String reason) {
        if (order.cancel(reason)) {
            log.info("Order {} CANCELLED ({})", order.getId(), reason);
        } else {
            log.warn("Order {} is {} - cancellation ignored", order.getId(), order.getStatus());
        }
    }
}
