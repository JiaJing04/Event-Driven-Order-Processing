package com.example.saga.common.events;

/** Kafka record header names. */
public final class EventHeaders {

    /** Globally unique id of the event (UUID). Used by consumers for de-duplication. */
    public static final String EVENT_ID = "event-id";

    /** Logical type of the event, see {@link EventTypes}. */
    public static final String EVENT_TYPE = "event-type";

    private EventHeaders() {
    }
}
