package com.example.saga.it;

import com.example.saga.common.events.EventTypes;
import com.example.saga.common.events.Topics;
import com.example.saga.common.idempotency.ProcessedMessageStore;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** HTTP idempotency (Idempotency-Key) and idempotent Kafka consumers. */
class IdempotencyIT extends AbstractSagaIT {

    // ------------------------------------------------------------------ HTTP: Idempotency-Key

    @Test
    void sameKeyAndBody_returnsTheOriginalOrder_andCreatesNothingTwice() {
        String sku = newSku();
        setStock(sku, 10);
        String key = "key-" + UUID.randomUUID();
        ObjectNode body = orderBody("grace", sku, 2, "10.00");

        Api.Response first = createOrder(key, body);
        Api.Response second = createOrder(key, body);

        assertThat(first.status()).isEqualTo(201);
        assertThat(second.status()).isEqualTo(200);
        assertThat(second.header("Idempotent-Replay")).isEqualTo("true");
        assertThat(idOf(second)).isEqualTo(idOf(first));

        UUID orderId = idOf(first);
        awaitOrderStatus(orderId, "CONFIRMED");

        assertThat(stock(sku)).isEqualTo(8); // reserved once, not twice
        assertThat(count(ORDER, "SELECT count(*) FROM outbox_events WHERE aggregate_id = ?", orderId.toString()))
                .isEqualTo(1);
    }

    @Test
    void sameKeyWithDifferentBody_isRejected() {
        String sku = newSku();
        setStock(sku, 10);
        String key = "key-" + UUID.randomUUID();

        assertThat(createOrder(key, orderBody("heidi", sku, 1, "10.00")).status()).isEqualTo(201);
        Api.Response conflicting = createOrder(key, orderBody("heidi", sku, 2, "10.00"));

        assertThat(conflicting.status()).isEqualTo(422);
    }

    @Test
    void missingIdempotencyKeyHeader_isABadRequest() {
        Api.Response response = Api.post(ORDER.baseUrl() + "/api/orders", Map.of(), orderBody("ivan", "ANY", 1, "1.00"));
        assertThat(response.status()).isEqualTo(400);
    }

    @Test
    void parallelRequestsWithTheSameKey_createExactlyOneOrder() throws Exception {
        String sku = newSku();
        setStock(sku, 10);
        String key = "key-" + UUID.randomUUID();
        ObjectNode body = orderBody("judy", sku, 1, "10.00");

        int callers = 6;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            List<Future<Api.Response>> futures = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                futures.add(pool.submit(() -> createOrder(key, body)));
            }
            List<Api.Response> responses = new ArrayList<>();
            for (Future<Api.Response> future : futures) {
                responses.add(future.get(30, TimeUnit.SECONDS));
            }

            assertThat(responses).extracting(Api.Response::status).containsOnly(200, 201);
            assertThat(responses.stream().filter(r -> r.status() == 201).count()).isEqualTo(1);
            assertThat(responses.stream().map(r -> idOf(r)).distinct().count()).isEqualTo(1);

            UUID orderId = idOf(responses.get(0));
            awaitOrderStatus(orderId, "CONFIRMED");
            assertThat(stock(sku)).isEqualTo(9);
        } finally {
            pool.shutdownNow();
        }
    }

    // ------------------------------------------------------------------ Kafka: idempotent consumer

    @Test
    void processedMessageStore_acceptsAnEventIdOnlyOnce() {
        ProcessedMessageStore store = INVENTORY.context().getBean(ProcessedMessageStore.class);
        TransactionTemplate tx = new TransactionTemplate(INVENTORY.context().getBean(PlatformTransactionManager.class));
        UUID eventId = UUID.randomUUID();

        assertThat(tx.<Boolean>execute(status -> store.markProcessed("it-consumer", eventId))).isTrue();
        assertThat(tx.<Boolean>execute(status -> store.markProcessed("it-consumer", eventId))).isFalse();
        // the same id for a different consumer is independent
        assertThat(tx.<Boolean>execute(status -> store.markProcessed("other-consumer", eventId))).isTrue();
    }

    @Test
    void duplicateKafkaDelivery_isAppliedOnce() {
        String sku = newSku();
        setStock(sku, 10);
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String key = orderId.toString();
        String json = orderCreatedJson(orderId, "kate", sku, 3, "10.00");

        // at-least-once delivery: the very same record arrives three times
        for (int i = 0; i < 3; i++) {
            publish(Topics.ORDER_EVENTS, key, eventId, EventTypes.ORDER_CREATED, json);
        }
        // a later, different event on the same partition (same key) proves the duplicates were consumed
        String marker = newSku();
        setStock(marker, 1);
        UUID markerOrderId = UUID.randomUUID();
        publish(Topics.ORDER_EVENTS, key, UUID.randomUUID(), EventTypes.ORDER_CREATED,
                orderCreatedJson(markerOrderId, "kate", marker, 1, "10.00"));

        eventually().untilAsserted(() -> assertThat(reservation(markerOrderId).status()).isEqualTo(200));

        assertThat(stock(sku)).isEqualTo(7);
        assertThat(count(INVENTORY, "SELECT count(*) FROM processed_messages WHERE event_id = ?", eventId))
                .isEqualTo(1);
        assertThat(count(INVENTORY, "SELECT count(*) FROM outbox_events WHERE aggregate_id = ?", orderId.toString()))
                .isEqualTo(1);
    }
}
