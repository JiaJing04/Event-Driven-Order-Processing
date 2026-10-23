package com.example.saga.common.events;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Event payloads exchanged between the services. They are the public contract of the saga:
 * every field is serialised as JSON into the Kafka record value.
 * The event id and the event type travel as Kafka headers (see {@link EventHeaders}).
 */
public final class Events {

    private Events() {
    }

    /** Published by order-service when a new order has been accepted (state PENDING). */
    public record OrderCreated(UUID orderId, String customerId, BigDecimal totalAmount, List<Item> items) {
        public record Item(String sku, int quantity, BigDecimal unitPrice) {
        }
    }

    /** Published by inventory-service when stock for every line has been reserved. */
    public record InventoryReserved(UUID orderId, String customerId, BigDecimal totalAmount) {
    }

    /** Published by inventory-service when the order cannot be fulfilled (unknown SKU / not enough stock). */
    public record InventoryRejected(UUID orderId, String reason) {
    }

    /** Published by payment-service when the customer has been charged. */
    public record PaymentCompleted(UUID orderId, UUID paymentId, BigDecimal amount) {
    }

    /** Published by payment-service when the charge was declined. Triggers compensation. */
    public record PaymentFailed(UUID orderId, String reason) {
    }
}
