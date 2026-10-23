package com.example.saga.it;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The saga end to end: HTTP -> order-service -> Kafka -> inventory-service -> Kafka -> payment-service
 * -> Kafka -> order-service, including the compensation paths.
 */
class SagaFlowIT extends AbstractSagaIT {

    @Test
    void happyPath_orderIsConfirmed_stockReserved_paymentCompleted() {
        String sku = newSku();
        setStock(sku, 10);

        Api.Response created = createOrder("alice", sku, 2, "25.00");

        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body().path("status").asText()).isEqualTo("PENDING");
        assertThat(created.body().path("totalAmount").decimalValue()).isEqualByComparingTo("50.00");
        UUID orderId = idOf(created);

        awaitOrderStatus(orderId, "CONFIRMED");

        assertThat(stock(sku)).isEqualTo(8);
        Api.Response reservation = reservation(orderId);
        assertThat(reservation.status()).isEqualTo(200);
        assertThat(reservation.body().path("status").asText()).isEqualTo("RESERVED");

        Api.Response payment = payment(orderId);
        assertThat(payment.status()).isEqualTo(200);
        assertThat(payment.body().path("status").asText()).isEqualTo("COMPLETED");
        assertThat(payment.body().path("amount").decimalValue()).isEqualByComparingTo("50.00");
    }

    @Test
    void insufficientStock_orderIsCancelled_noPaymentIsTaken() {
        String sku = newSku();
        setStock(sku, 1);

        UUID orderId = idOf(createOrder("bob", sku, 5, "10.00"));

        awaitOrderStatus(orderId, "CANCELLED");
        assertThat(order(orderId).path("failureReason").asText()).contains("Insufficient stock");
        assertThat(stock(sku)).isEqualTo(1);
        assertThat(reservation(orderId).status()).isEqualTo(404);
        assertThat(payment(orderId).status()).isEqualTo(404);
    }

    @Test
    void unknownSku_orderIsCancelled() {
        UUID orderId = idOf(createOrder("carol", "DOES-NOT-EXIST-" + UUID.randomUUID(), 1, "10.00"));

        awaitOrderStatus(orderId, "CANCELLED");
        assertThat(order(orderId).path("failureReason").asText()).contains("Unknown SKU");
    }

    @Test
    void paymentDeclined_reservationIsReleased_orderIsCancelled() {
        String sku = newSku();
        setStock(sku, 5);

        UUID orderId = idOf(createOrder("dave-DECLINE-me", sku, 3, "10.00"));

        awaitOrderStatus(orderId, "CANCELLED");
        assertThat(order(orderId).path("failureReason").asText()).contains("Payment failed");

        // compensation in inventory-service runs independently of the order update, so wait for it
        eventually().untilAsserted(() -> {
            assertThat(stock(sku)).isEqualTo(5);
            assertThat(reservation(orderId).body().path("status").asText()).isEqualTo("RELEASED");
        });
        assertThat(payment(orderId).body().path("status").asText()).isEqualTo("FAILED");
    }

    @Test
    void paymentAboveGatewayLimit_isDeclined() {
        String sku = newSku();
        setStock(sku, 5);

        UUID orderId = idOf(createOrder("erin", sku, 2, "600.00")); // 1200 > default limit of 1000

        awaitOrderStatus(orderId, "CANCELLED");
        eventually().untilAsserted(() -> assertThat(stock(sku)).isEqualTo(5));
    }

    @Test
    void concurrentOrdersForTheLastUnit_exactlyOneWins() throws Exception {
        String sku = newSku();
        setStock(sku, 1);

        int buyers = 5;
        ExecutorService pool = Executors.newFixedThreadPool(buyers);
        try {
            List<Future<UUID>> futures = new ArrayList<>();
            for (int i = 0; i < buyers; i++) {
                String customer = "buyer-" + i;
                futures.add(pool.submit(() -> idOf(createOrder(customer, sku, 1, "10.00"))));
            }
            List<UUID> orderIds = new ArrayList<>();
            for (Future<UUID> future : futures) {
                orderIds.add(future.get(30, TimeUnit.SECONDS));
            }

            eventually().untilAsserted(() -> {
                long confirmed = orderIds.stream().filter(id -> "CONFIRMED".equals(orderStatus(id))).count();
                long cancelled = orderIds.stream().filter(id -> "CANCELLED".equals(orderStatus(id))).count();
                assertThat(confirmed).isEqualTo(1);
                assertThat(cancelled).isEqualTo(buyers - 1);
            });
            assertThat(stock(sku)).isZero();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void invalidRequestIsRejectedWithoutSideEffects() {
        long before = count(ORDER, "SELECT count(*) FROM orders");

        Api.Response response = createOrder("frank", "ANY", 0, "10.00"); // quantity must be >= 1

        assertThat(response.status()).isEqualTo(400);
        assertThat(count(ORDER, "SELECT count(*) FROM orders")).isEqualTo(before);
    }
}
