package com.example.saga.common.kafka;

import com.example.saga.common.events.EventHeaders;
import com.example.saga.common.events.Events;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KafkaEventsTest {

    private final KafkaEvents events = new KafkaEvents(new ObjectMapper());

    @Test
    void readsEnvelopeAndPayload() {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        ConsumerRecord<String, String> record = record("""
                {"orderId":"%s","customerId":"c-1","totalAmount":20.50,
                 "items":[{"sku":"A","quantity":2,"unitPrice":10.25}]}""".formatted(orderId));
        record.headers().add(EventHeaders.EVENT_ID, eventId.toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add(EventHeaders.EVENT_TYPE, "OrderCreated".getBytes(StandardCharsets.UTF_8));

        assertThat(events.eventId(record)).isEqualTo(eventId);
        assertThat(events.eventType(record)).isEqualTo("OrderCreated");

        Events.OrderCreated payload = events.read(record, Events.OrderCreated.class);
        assertThat(payload.orderId()).isEqualTo(orderId);
        assertThat(payload.totalAmount()).isEqualByComparingTo(new BigDecimal("20.50"));
        assertThat(payload.items()).hasSize(1);
        assertThat(payload.items().get(0).sku()).isEqualTo("A");
    }

    @Test
    void missingEventIdHeaderIsMalformed() {
        ConsumerRecord<String, String> record = record("{}");
        assertThatThrownBy(() -> events.eventId(record)).isInstanceOf(MalformedEventException.class);
    }

    @Test
    void invalidJsonIsMalformed() {
        ConsumerRecord<String, String> record = record("not json");
        assertThatThrownBy(() -> events.read(record, Events.PaymentFailed.class))
                .isInstanceOf(MalformedEventException.class);
    }

    private static ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("order.events", 0, 0L, "key", value);
    }
}
