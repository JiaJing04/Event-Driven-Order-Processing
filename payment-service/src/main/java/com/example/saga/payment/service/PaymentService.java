package com.example.saga.payment.service;

import com.example.saga.common.events.EventTypes;
import com.example.saga.common.events.Events;
import com.example.saga.common.events.Topics;
import com.example.saga.common.idempotency.ProcessedMessageStore;
import com.example.saga.common.outbox.OutboxService;
import com.example.saga.payment.domain.Payment;
import com.example.saga.payment.domain.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Saga participant #2: charges the customer once stock has been reserved. */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final String CONSUMER = "payment-service";
    private static final String AGGREGATE_TYPE = "Payment";

    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final ProcessedMessageStore processed;
    private final OutboxService outbox;

    public PaymentService(PaymentRepository payments,
                          PaymentGateway gateway,
                          ProcessedMessageStore processed,
                          OutboxService outbox) {
        this.payments = payments;
        this.gateway = gateway;
        this.processed = processed;
        this.outbox = outbox;
    }

    @Transactional
    public void onInventoryReserved(UUID eventId, Events.InventoryReserved event) {
        if (!processed.markProcessed(CONSUMER, eventId)) {
            log.info("Duplicate InventoryReserved {} ignored", eventId);
            return;
        }
        UUID orderId = event.orderId();
        if (payments.existsByOrderId(orderId)) {
            log.warn("Order {} already has a payment - ignoring InventoryReserved {}", orderId, eventId);
            return;
        }

        UUID paymentId = UUID.randomUUID();
        PaymentGateway.Result result = gateway.charge(orderId, event.customerId(), event.totalAmount());

        if (result.approved()) {
            payments.save(Payment.completed(paymentId, orderId, event.customerId(), event.totalAmount()));
            outbox.append(AGGREGATE_TYPE, orderId.toString(), Topics.PAYMENT_EVENTS, EventTypes.PAYMENT_COMPLETED,
                    new Events.PaymentCompleted(orderId, paymentId, event.totalAmount()));
            log.info("Order {} paid ({})", orderId, event.totalAmount());
        } else {
            payments.save(Payment.failed(paymentId, orderId, event.customerId(), event.totalAmount(), result.declineReason()));
            outbox.append(AGGREGATE_TYPE, orderId.toString(), Topics.PAYMENT_EVENTS, EventTypes.PAYMENT_FAILED,
                    new Events.PaymentFailed(orderId, result.declineReason()));
            log.info("Order {} payment declined: {}", orderId, result.declineReason());
        }
    }
}
