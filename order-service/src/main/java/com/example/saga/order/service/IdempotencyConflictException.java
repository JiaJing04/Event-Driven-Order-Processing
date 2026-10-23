package com.example.saga.order.service;

/** The same Idempotency-Key was re-used with a different request body. */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String key) {
        super("Idempotency-Key '" + key + "' was already used with a different request payload");
    }
}
