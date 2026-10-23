package com.example.saga.common.outbox;

import com.example.saga.common.config.SagaProperties;
import com.example.saga.common.events.EventHeaders;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Polling publisher: moves committed outbox rows to Kafka.
 * <ul>
 *   <li>Rows are claimed with {@code FOR UPDATE SKIP LOCKED}, so several instances of a service can run the
 *       relay concurrently without publishing the same row twice at the same time.</li>
 *   <li>A row is marked as published only after Kafka acknowledged it. If the process dies between
 *       "acked" and "marked", the row is published again, which gives <b>at-least-once</b> delivery.
 *       Consumers de-duplicate on the {@code event-id} header.</li>
 *   <li>Publishing stops at the first failure so the order of events is preserved.</li>
 * </ul>
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private static final String SELECT_PENDING = """
            SELECT id, event_id, topic, message_key, event_type, payload
              FROM outbox_events
             WHERE published_at IS NULL
             ORDER BY id
             LIMIT ?
               FOR UPDATE SKIP LOCKED
            """;

    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate tx;
    private final SagaProperties.Outbox properties;

    public OutboxRelay(JdbcTemplate jdbc,
                       KafkaTemplate<String, String> kafka,
                       PlatformTransactionManager transactionManager,
                       SagaProperties properties) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.tx = new TransactionTemplate(transactionManager);
        this.properties = properties.outbox();
    }

    @Scheduled(fixedDelayString = "${saga.outbox.poll-interval-ms:500}")
    public void poll() {
        try {
            int published;
            do {
                published = publishBatch();
            } while (published >= properties.batchSize());
        } catch (RuntimeException ex) {
            log.warn("Outbox poll failed, will retry: {}", ex.toString());
        }
    }

    /** Housekeeping: delete rows that were published long ago. */
    @Scheduled(fixedDelayString = "${saga.outbox.cleanup-interval-ms:3600000}",
            initialDelayString = "${saga.outbox.cleanup-interval-ms:3600000}")
    public void cleanup() {
        try {
            int deleted = jdbc.update(
                    "DELETE FROM outbox_events WHERE published_at IS NOT NULL AND published_at < ?",
                    OffsetDateTime.now().minus(properties.retention()));
            if (deleted > 0) {
                log.debug("Removed {} published outbox rows", deleted);
            }
        } catch (RuntimeException ex) {
            log.warn("Outbox cleanup failed: {}", ex.toString());
        }
    }

    /**
     * Publishes one batch in a single DB transaction.
     *
     * @return number of rows successfully published
     */
    public int publishBatch() {
        Integer result = tx.execute(status -> {
            List<OutboxRow> rows = jdbc.query(SELECT_PENDING, (rs, i) -> new OutboxRow(
                    rs.getLong("id"),
                    rs.getObject("event_id", UUID.class),
                    rs.getString("topic"),
                    rs.getString("message_key"),
                    rs.getString("event_type"),
                    rs.getString("payload")), properties.batchSize());

            int sent = 0;
            for (OutboxRow row : rows) {
                if (!send(row)) {
                    break; // keep ordering: do not skip ahead of a failed row
                }
                jdbc.update("UPDATE outbox_events SET published_at = now() WHERE id = ?", row.id());
                sent++;
            }
            return sent;
        });
        return result == null ? 0 : result;
    }

    private boolean send(OutboxRow row) {
        ProducerRecord<String, String> record = new ProducerRecord<>(row.topic(), row.key(), row.payload());
        record.headers().add(EventHeaders.EVENT_ID, row.eventId().toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add(EventHeaders.EVENT_TYPE, row.eventType().getBytes(StandardCharsets.UTF_8));
        try {
            kafka.send(record).get(properties.sendTimeoutSeconds(), TimeUnit.SECONDS);
            log.debug("Published {} {} to {}", row.eventType(), row.eventId(), row.topic());
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException ex) {
            log.warn("Could not publish outbox event {} ({}): {}", row.eventId(), row.eventType(), ex.toString());
            return false;
        }
    }

    private record OutboxRow(long id, UUID eventId, String topic, String key, String eventType, String payload) {
    }
}
