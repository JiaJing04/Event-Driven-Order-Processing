package com.example.saga.it;

import com.example.saga.common.events.EventHeaders;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Helpers shared by all end-to-end tests. */
abstract class AbstractSagaIT {

    protected static final SagaEnvironment.RunningService ORDER = SagaEnvironment.ORDER;
    protected static final SagaEnvironment.RunningService INVENTORY = SagaEnvironment.INVENTORY;
    protected static final SagaEnvironment.RunningService PAYMENT = SagaEnvironment.PAYMENT;

    // --------------------------------------------------------------------------------- test data

    protected static String newSku() {
        return "IT-" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected static ObjectNode orderBody(String customerId, String sku, int quantity, String unitPrice) {
        ObjectNode body = Api.JSON.createObjectNode();
        body.put("customerId", customerId);
        ArrayNode items = body.putArray("items");
        ObjectNode item = items.addObject();
        item.put("sku", sku);
        item.put("quantity", quantity);
        item.put("unitPrice", new BigDecimal(unitPrice));
        return body;
    }

    // --------------------------------------------------------------------------------- API calls

    protected static void setStock(String sku, int quantity) {
        ObjectNode body = Api.JSON.createObjectNode();
        body.put("availableQuantity", quantity);
        Api.Response response = Api.put(INVENTORY.baseUrl() + "/api/stock/" + sku, body);
        assertThat(response.status()).isEqualTo(200);
    }

    protected static int stock(String sku) {
        Api.Response response = Api.get(INVENTORY.baseUrl() + "/api/stock/" + sku);
        assertThat(response.status()).isEqualTo(200);
        return response.body().path("availableQuantity").asInt();
    }

    protected static Api.Response createOrder(String idempotencyKey, ObjectNode body) {
        return Api.post(ORDER.baseUrl() + "/api/orders", Map.of("Idempotency-Key", idempotencyKey), body);
    }

    protected static Api.Response createOrder(String customerId, String sku, int quantity, String unitPrice) {
        return createOrder(UUID.randomUUID().toString(), orderBody(customerId, sku, quantity, unitPrice));
    }

    protected static UUID idOf(Api.Response created) {
        assertThat(created.status()).isIn(200, 201);
        return UUID.fromString(created.body().path("id").asText());
    }

    protected static JsonNode order(UUID id) {
        return Api.get(ORDER.baseUrl() + "/api/orders/" + id).body();
    }

    protected static String orderStatus(UUID id) {
        return order(id).path("status").asText();
    }

    protected static Api.Response reservation(UUID orderId) {
        return Api.get(INVENTORY.baseUrl() + "/api/reservations/" + orderId);
    }

    protected static Api.Response payment(UUID orderId) {
        return Api.get(PAYMENT.baseUrl() + "/api/payments/by-order/" + orderId);
    }

    // --------------------------------------------------------------------------------- waiting

    protected static ConditionFactory eventually() {
        return Awaitility.await()
                .atMost(Duration.ofSeconds(45))
                .pollInterval(Duration.ofMillis(200));
    }

    protected static void awaitOrderStatus(UUID orderId, String expected) {
        eventually().untilAsserted(() -> assertThat(orderStatus(orderId)).isEqualTo(expected));
    }

    // --------------------------------------------------------------------------------- database

    protected static long count(SagaEnvironment.RunningService service, String sql, Object... params) {
        try (Connection connection = DriverManager.getConnection(
                service.jdbcUrl(), SagaEnvironment.DB_USER, SagaEnvironment.DB_PASSWORD);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    // --------------------------------------------------------------------------------- Kafka

    /** Publishes a raw saga event, exactly as the outbox relay would (same headers, key = aggregate id). */
    protected static void publish(String topic, String key, UUID eventId, String eventType, String json) {
        Properties props = new Properties();
        props.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, SagaEnvironment.KAFKA.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, json);
            record.headers().add(EventHeaders.EVENT_ID, eventId.toString().getBytes(StandardCharsets.UTF_8));
            record.headers().add(EventHeaders.EVENT_TYPE, eventType.getBytes(StandardCharsets.UTF_8));
            producer.send(record).get(15, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        } catch (ExecutionException | java.util.concurrent.TimeoutException ex) {
            throw new IllegalStateException("Could not publish test event", ex);
        }
    }

    protected static String orderCreatedJson(UUID orderId, String customerId, String sku, int quantity, String unitPrice) {
        BigDecimal price = new BigDecimal(unitPrice);
        BigDecimal total = price.multiply(BigDecimal.valueOf(quantity));
        return """
                {"orderId":"%s","customerId":"%s","totalAmount":%s,
                 "items":[{"sku":"%s","quantity":%d,"unitPrice":%s}]}"""
                .formatted(orderId, customerId, total.toPlainString(), sku, quantity, price.toPlainString());
    }
}
