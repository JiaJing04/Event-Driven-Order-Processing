package com.example.saga.common.events;

/** Kafka topic names. Every service owns exactly one "events" topic and publishes only to it. */
public final class Topics {

    public static final String ORDER_EVENTS = "order.events";
    public static final String INVENTORY_EVENTS = "inventory.events";
    public static final String PAYMENT_EVENTS = "payment.events";

    private Topics() {
    }

    /** Dead-letter topic for records that kept failing. */
    public static String deadLetter(String topic) {
        return topic + ".DLT";
    }
}
