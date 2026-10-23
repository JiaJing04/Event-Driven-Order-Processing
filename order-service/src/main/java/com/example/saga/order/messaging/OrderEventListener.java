package com.example.saga.order.messaging;

import com.example.saga.common.events.EventTypes;
import com.example.saga.common.events.Events;
import com.example.saga.common.events.Topics;
import com.example.saga.common.kafka.KafkaEvents;
import com.example.saga.order.service.OrderSagaHandler;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Kafka entry point of order-service: translates records into calls on the transactional handler. */
@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final KafkaEvents events;
    private final OrderSagaHandler handler;

    public OrderEventListener(KafkaEvents events, OrderSagaHandler handler) {
        this.events = events;
        this.handler = handler;
    }

    @KafkaListener(topics = {Topics.INVENTORY_EVENTS, Topics.PAYMENT_EVENTS}, groupId = "${spring.application.name}")
    public void onEvent(ConsumerRecord<String, String> record) {
        String type = events.eventType(record);
        UUID eventId = events.eventId(record);
        switch (type) {
            case EventTypes.PAYMENT_COMPLETED ->
                    handler.onPaymentCompleted(eventId, events.read(record, Events.PaymentCompleted.class));
            case EventTypes.PAYMENT_FAILED ->
                    handler.onPaymentFailed(eventId, events.read(record, Events.PaymentFailed.class));
            case EventTypes.INVENTORY_REJECTED ->
                    handler.onInventoryRejected(eventId, events.read(record, Events.InventoryRejected.class));
            default -> log.debug("Ignoring {} on {}", type, record.topic());
        }
    }
}
