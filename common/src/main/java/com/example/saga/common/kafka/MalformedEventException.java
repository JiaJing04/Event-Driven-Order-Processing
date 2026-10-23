package com.example.saga.common.kafka;

/**
 * Thrown when a Kafka record cannot be understood (missing header, invalid JSON).
 * Retrying cannot help, so the error handler sends such records straight to the dead-letter topic.
 */
public class MalformedEventException extends RuntimeException {

    public MalformedEventException(String message) {
        super(message);
    }

    public MalformedEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
