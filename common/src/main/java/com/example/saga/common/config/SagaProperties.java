package com.example.saga.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Tunables shared by all services. Everything has a default, so none of it has to be configured.
 * <p>
 * Additionally read directly by {@code @Scheduled}: {@code saga.outbox.poll-interval-ms} (default 500) and
 * {@code saga.outbox.cleanup-interval-ms} (default 1h).
 */
@ConfigurationProperties(prefix = "saga")
public record SagaProperties(@DefaultValue Outbox outbox,
                             @DefaultValue Topics topics,
                             @DefaultValue Consumer consumer) {

    /**
     * @param batchSize          max rows published per transaction
     * @param sendTimeoutSeconds how long to wait for a broker ack per record
     * @param retention          how long published rows are kept before cleanup
     */
    public record Outbox(@DefaultValue("100") int batchSize,
                         @DefaultValue("10") long sendTimeoutSeconds,
                         @DefaultValue("24h") Duration retention) {
    }

    /** Settings used when topics are auto-created at start-up. */
    public record Topics(@DefaultValue("3") int partitions,
                         @DefaultValue("1") int replicas) {
    }

    /**
     * @param retryBackoffMs pause between processing attempts of a failing record
     * @param retryAttempts  number of retries before the record goes to the dead-letter topic
     */
    public record Consumer(@DefaultValue("1000") long retryBackoffMs,
                           @DefaultValue("3") long retryAttempts) {
    }
}
