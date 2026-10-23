package com.example.saga.inventory.messaging;

import com.example.saga.common.events.EventTypes;
import com.example.saga.common.events.Events;
import com.example.saga.common.events.Topics;
import com.example.saga.common.kafka.KafkaEvents;
import com.example.saga.inventory.service.InventoryService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class InventoryEventListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventListener.class);

    private final KafkaEvents events;
    private final InventoryService service;

    public InventoryEventListener(KafkaEvents events, InventoryService service) {
        this.events = events;
        this.service = service;
    }

    @KafkaListener(topics = {Topics.ORDER_EVENTS, Topics.PAYMENT_EVENTS}, groupId = "${spring.application.name}")
    public void onEvent(ConsumerRecord<String, String> record) {
        String type = events.eventType(record);
        UUID eventId = events.eventId(record);
        switch (type) {
            case EventTypes.ORDER_CREATED ->
                    service.onOrderCreated(eventId, events.read(record, Events.OrderCreated.class));
            case EventTypes.PAYMENT_FAILED ->
                    service.onPaymentFailed(eventId, events.read(record, Events.PaymentFailed.class));
            default -> log.debug("Ignoring {} on {}", type, record.topic());
        }
    }
}
