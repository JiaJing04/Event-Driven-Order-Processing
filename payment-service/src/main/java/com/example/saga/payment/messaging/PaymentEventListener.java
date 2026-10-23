package com.example.saga.payment.messaging;

import com.example.saga.common.events.EventTypes;
import com.example.saga.common.events.Events;
import com.example.saga.common.events.Topics;
import com.example.saga.common.kafka.KafkaEvents;
import com.example.saga.payment.service.PaymentService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final KafkaEvents events;
    private final PaymentService service;

    public PaymentEventListener(KafkaEvents events, PaymentService service) {
        this.events = events;
        this.service = service;
    }

    @KafkaListener(topics = Topics.INVENTORY_EVENTS, groupId = "${spring.application.name}")
    public void onEvent(ConsumerRecord<String, String> record) {
        String type = events.eventType(record);
        UUID eventId = events.eventId(record);
        switch (type) {
            case EventTypes.INVENTORY_RESERVED ->
                    service.onInventoryReserved(eventId, events.read(record, Events.InventoryReserved.class));
            default -> log.debug("Ignoring {} on {}", type, record.topic());
        }
    }
}
