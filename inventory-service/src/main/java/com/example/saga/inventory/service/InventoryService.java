package com.example.saga.inventory.service;

import com.example.saga.common.events.EventTypes;
import com.example.saga.common.events.Events;
import com.example.saga.common.events.Topics;
import com.example.saga.common.idempotency.ProcessedMessageStore;
import com.example.saga.common.outbox.OutboxService;
import com.example.saga.inventory.domain.Reservation;
import com.example.saga.inventory.domain.ReservationLine;
import com.example.saga.inventory.domain.ReservationRepository;
import com.example.saga.inventory.domain.StockItem;
import com.example.saga.inventory.domain.StockItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Saga participant #1. Every public method is a single local ACID transaction that
 * (1) records the incoming event as processed, (2) changes stock/reservation state and
 * (3) appends the outgoing event to the outbox.
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
    private static final String CONSUMER = "inventory-service";
    private static final String AGGREGATE_TYPE = "Reservation";

    private final StockItemRepository stock;
    private final ReservationRepository reservations;
    private final ProcessedMessageStore processed;
    private final OutboxService outbox;

    public InventoryService(StockItemRepository stock,
                            ReservationRepository reservations,
                            ProcessedMessageStore processed,
                            OutboxService outbox) {
        this.stock = stock;
        this.reservations = reservations;
        this.processed = processed;
        this.outbox = outbox;
    }

    /** Forward step: try to reserve stock for a newly created order. */
    @Transactional
    public void onOrderCreated(UUID eventId, Events.OrderCreated event) {
        if (!processed.markProcessed(CONSUMER, eventId)) {
            log.info("Duplicate OrderCreated {} ignored", eventId);
            return;
        }
        UUID orderId = event.orderId();
        if (reservations.existsById(orderId)) {
            log.warn("Order {} already has a reservation - ignoring OrderCreated {}", orderId, eventId);
            return;
        }

        // Merge duplicate SKUs; TreeMap keeps SKU order for deterministic locking.
        Map<String, Integer> required = new TreeMap<>();
        for (Events.OrderCreated.Item item : event.items()) {
            required.merge(item.sku(), item.quantity(), Integer::sum);
        }

        Map<String, StockItem> locked = stock.findAllForUpdate(required.keySet()).stream()
                .collect(Collectors.toMap(StockItem::getSku, Function.identity()));

        String problem = findProblem(required, locked);
        if (problem != null) {
            log.info("Order {} rejected: {}", orderId, problem);
            outbox.append(AGGREGATE_TYPE, orderId.toString(), Topics.INVENTORY_EVENTS, EventTypes.INVENTORY_REJECTED,
                    new Events.InventoryRejected(orderId, problem));
            return;
        }

        required.forEach((sku, quantity) -> locked.get(sku).reserve(quantity));
        List<ReservationLine> lines = required.entrySet().stream()
                .map(e -> new ReservationLine(e.getKey(), e.getValue()))
                .toList();
        reservations.save(Reservation.reserved(orderId, lines));

        outbox.append(AGGREGATE_TYPE, orderId.toString(), Topics.INVENTORY_EVENTS, EventTypes.INVENTORY_RESERVED,
                new Events.InventoryReserved(orderId, event.customerId(), event.totalAmount()));
        log.info("Order {} stock reserved: {}", orderId, required);
    }

    /** Compensation: payment failed, so give the reserved stock back. */
    @Transactional
    public void onPaymentFailed(UUID eventId, Events.PaymentFailed event) {
        if (!processed.markProcessed(CONSUMER, eventId)) {
            log.info("Duplicate PaymentFailed {} ignored", eventId);
            return;
        }
        reservations.findById(event.orderId()).ifPresentOrElse(reservation -> {
            if (!reservation.release()) {
                log.info("Reservation for order {} already released", event.orderId());
                return;
            }
            Map<String, Integer> toRestore = new TreeMap<>();
            reservation.getItems().forEach(line -> toRestore.merge(line.getSku(), line.getQuantity(), Integer::sum));
            stock.findAllForUpdate(toRestore.keySet())
                    .forEach(item -> item.release(toRestore.get(item.getSku())));
            log.info("Order {} reservation RELEASED (compensation): {}", event.orderId(), toRestore);
        }, () -> log.info("PaymentFailed for order {} without reservation - nothing to compensate", event.orderId()));
    }

    private static String findProblem(Map<String, Integer> required, Map<String, StockItem> locked) {
        for (Map.Entry<String, Integer> line : required.entrySet()) {
            StockItem item = locked.get(line.getKey());
            if (item == null) {
                return "Unknown SKU " + line.getKey();
            }
            if (item.getAvailableQuantity() < line.getValue()) {
                return "Insufficient stock for " + line.getKey() + ": requested " + line.getValue()
                        + ", available " + item.getAvailableQuantity();
            }
        }
        return null;
    }
}
