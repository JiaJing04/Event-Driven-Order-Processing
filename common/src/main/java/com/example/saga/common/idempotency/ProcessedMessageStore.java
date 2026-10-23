package com.example.saga.common.idempotency;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Idempotent-consumer support ("inbox" table).
 * <p>
 * A handler calls {@link #markProcessed} as the first statement of its business transaction. The insert
 * is committed or rolled back together with the business changes, so "event handled" and "effects
 * applied" can never diverge. A redelivered event hits the primary key and is skipped.
 */
public class ProcessedMessageStore {

    private final JdbcTemplate jdbc;

    public ProcessedMessageStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param consumer logical name of the handler (scopes the event id)
     * @param eventId  id from the {@code event-id} header
     * @return {@code true} if this is the first time the event is seen; {@code false} for a duplicate
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean markProcessed(String consumer, UUID eventId) {
        // Concurrent duplicates block on the unique index until the first transaction finishes,
        // then fall through to DO NOTHING (0 rows).
        int inserted = jdbc.update(
                "INSERT INTO processed_messages (consumer, event_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                consumer, eventId);
        return inserted == 1;
    }
}
