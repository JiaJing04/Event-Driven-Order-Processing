package com.example.saga.order.service;

import com.example.saga.common.events.EventTypes;
import com.example.saga.common.events.Events;
import com.example.saga.common.events.Topics;
import com.example.saga.common.outbox.OutboxService;
import com.example.saga.order.api.CreateOrderRequest;
import com.example.saga.order.api.OrderResponse;
import com.example.saga.order.domain.Order;
import com.example.saga.order.domain.OrderLine;
import com.example.saga.order.domain.OrderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final String AGGREGATE_TYPE = "Order";

    /** @param created true if a new order was created, false if an earlier identical request was replayed */
    public record CreateResult(OrderResponse order, boolean created) {
    }

    private final OrderRepository orders;
    private final IdempotencyKeyStore idempotencyKeys;
    private final OutboxService outbox;
    private final ObjectMapper mapper;

    public OrderService(OrderRepository orders,
                        IdempotencyKeyStore idempotencyKeys,
                        OutboxService outbox,
                        ObjectMapper mapper) {
        this.orders = orders;
        this.idempotencyKeys = idempotencyKeys;
        this.outbox = outbox;
        this.mapper = mapper;
    }

    /**
     * Creates the order, the idempotency record and the {@code OrderCreated} outbox row in ONE transaction.
     * Repeating the call with the same key + same body returns the original order and has no side effects.
     */
    @Transactional
    public CreateResult create(String idempotencyKey, CreateOrderRequest request) {
        String hash = fingerprint(request);
        UUID orderId = UUID.randomUUID();

        if (!idempotencyKeys.tryRegister(idempotencyKey, hash, orderId)) {
            IdempotencyKeyStore.Entry existing = idempotencyKeys.find(idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("Idempotency key vanished: " + idempotencyKey));
            if (!existing.requestHash().equals(hash)) {
                throw new IdempotencyConflictException(idempotencyKey);
            }
            Order order = orders.findById(existing.orderId())
                    .orElseThrow(() -> new OrderNotFoundException(existing.orderId()));
            log.info("Replaying order {} for idempotency key {}", order.getId(), idempotencyKey);
            return new CreateResult(OrderResponse.from(order), false);
        }

        List<OrderLine> lines = request.items().stream()
                .map(i -> new OrderLine(i.sku(), i.quantity(), i.unitPrice()))
                .toList();
        Order order = orders.save(Order.create(orderId, request.customerId(), lines));

        outbox.append(AGGREGATE_TYPE, orderId.toString(), Topics.ORDER_EVENTS, EventTypes.ORDER_CREATED,
                new Events.OrderCreated(
                        orderId,
                        order.getCustomerId(),
                        order.getTotalAmount(),
                        lines.stream()
                                .map(l -> new Events.OrderCreated.Item(l.getSku(), l.getQuantity(), l.getUnitPrice()))
                                .toList()));

        log.info("Order {} created for customer {}", orderId, order.getCustomerId());
        return new CreateResult(OrderResponse.from(order), true);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID id) {
        return orders.findById(id).map(OrderResponse::from).orElseThrow(() -> new OrderNotFoundException(id));
    }

    private String fingerprint(CreateOrderRequest request) {
        try {
            byte[] canonical = mapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Cannot fingerprint request", ex);
        }
    }
}
