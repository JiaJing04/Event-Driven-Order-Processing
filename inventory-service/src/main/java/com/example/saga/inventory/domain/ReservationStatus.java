package com.example.saga.inventory.domain;

public enum ReservationStatus {
    /** Stock is held for the order. */
    RESERVED,
    /** Stock was returned to the pool (compensation after a payment failure). */
    RELEASED
}
