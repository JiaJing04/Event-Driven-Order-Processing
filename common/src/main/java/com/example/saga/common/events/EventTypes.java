package com.example.saga.common.events;

/** Values of the {@code event-type} Kafka header. Constants so they can be used in {@code switch} labels. */
public final class EventTypes {

    public static final String ORDER_CREATED = "OrderCreated";
    public static final String INVENTORY_RESERVED = "InventoryReserved";
    public static final String INVENTORY_REJECTED = "InventoryRejected";
    public static final String PAYMENT_COMPLETED = "PaymentCompleted";
    public static final String PAYMENT_FAILED = "PaymentFailed";

    private EventTypes() {
    }
}
