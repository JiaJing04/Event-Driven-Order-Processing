package com.example.saga.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Write side of the transactional outbox.
 * <p>
 * Business code calls {@link #append} <em>inside the same database transaction</em> that changes its
 * aggregate. The event is therefore committed atomically with the state change, or not at all.
 * {@link OutboxRelay} later ships the rows to Kafka.
 */
public class OutboxService {

    private static final String INSERT = """
            INSERT INTO outbox_events (event_id, aggregate_type, aggregate_id, topic, message_key, event_type, payload)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public OutboxService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /**
     * @param aggregateType e.g. "Order" (informational)
     * @param aggregateId   id of the aggregate; also used as Kafka message key so that all events of one
     *                      aggregate land on the same partition and stay ordered
     * @param topic         destination topic
     * @param eventType     value of the {@code event-type} header
     * @param payload       any Jackson-serialisable object
     * @return the generated event id (sent as {@code event-id} header)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID append(String aggregateType, String aggregateId, String topic, String eventType, Object payload) {
        UUID eventId = UUID.randomUUID();
        String json;
        try {
            json = mapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialise " + eventType + " event", ex);
        }
        jdbc.update(INSERT, eventId, aggregateType, aggregateId, topic, aggregateId, eventType, json);
        return eventId;
    }
}
