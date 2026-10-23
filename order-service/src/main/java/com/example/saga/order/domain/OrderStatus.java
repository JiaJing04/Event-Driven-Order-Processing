package com.example.saga.order.domain;

public enum OrderStatus {
    /** Accepted, saga in progress. */
    PENDING,
    /** Stock reserved and payment completed. Terminal. */
    CONFIRMED,
    /** Stock could not be reserved or payment failed. Terminal. */
    CANCELLED
}
