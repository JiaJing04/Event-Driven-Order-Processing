package com.example.saga.common.kafka;

import com.example.saga.common.events.EventHeaders;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Small helper that extracts the event envelope (headers) and payload from a consumed record. */
public class KafkaEvents {

    private final ObjectMapper mapper;

    public KafkaEvents(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public UUID eventId(ConsumerRecord<?, ?> record) {
        String value = header(record, EventHeaders.EVENT_ID);
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new MalformedEventException("Header '" + EventHeaders.EVENT_ID + "' is not a UUID: " + value, ex);
        }
    }

    public String eventType(ConsumerRecord<?, ?> record) {
        return header(record, EventHeaders.EVENT_TYPE);
    }

    public <T> T read(ConsumerRecord<String, String> record, Class<T> type) {
        try {
            return mapper.readValue(record.value(), type);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new MalformedEventException(
                    "Cannot parse " + type.getSimpleName() + " from " + record.topic() + "@" + record.offset(), ex);
        }
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        if (header == null || header.value() == null) {
            throw new MalformedEventException("Missing header '" + name + "' on " + record.topic() + "@" + record.offset());
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
