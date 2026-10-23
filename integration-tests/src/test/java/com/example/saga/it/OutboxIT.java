package com.example.saga.it;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Transactional outbox behaviour, including a broker outage. */
class OutboxIT extends AbstractSagaIT {

    @Test
    void everyServiceMarksItsOutboxRowsAsPublished() {
        String sku = newSku();
        setStock(sku, 3);
        UUID orderId = idOf(createOrder("leo", sku, 1, "10.00"));
        awaitOrderStatus(orderId, "CONFIRMED");

        // order: OrderCreated; inventory: InventoryReserved; payment: PaymentCompleted
        for (SagaEnvironment.RunningService service : new SagaEnvironment.RunningService[]{ORDER, INVENTORY, PAYMENT}) {
            String id = orderId.toString();
            assertThat(count(service, "SELECT count(*) FROM outbox_events WHERE aggregate_id = ?", id))
                    .as("%s outbox rows", service.name()).isEqualTo(1);
            eventually().untilAsserted(() ->
                    assertThat(count(service,
                            "SELECT count(*) FROM outbox_events WHERE aggregate_id = ? AND published_at IS NULL", id))
                            .as("%s unpublished rows", service.name()).isZero());
        }
    }

    /**
     * The point of the pattern: a business transaction must not depend on the broker being up.
     * Orders are accepted while Kafka is frozen, their events wait in the outbox table, and the saga
     * simply continues once the broker is back.
     */
    @Test
    void orderIsAcceptedWhileBrokerIsDown_andTheSagaCompletesAfterRecovery() throws Exception {
        String sku = newSku();
        setStock(sku, 3);
        var docker = SagaEnvironment.KAFKA.getDockerClient();
        String containerId = SagaEnvironment.KAFKA.getContainerId();

        docker.pauseContainerCmd(containerId).exec();
        UUID orderId;
        try {
            Api.Response created = createOrder("mia", sku, 1, "10.00");
            assertThat(created.status()).isEqualTo(201); // no dependency on Kafka in the request path
            orderId = idOf(created);

            Thread.sleep(2_000);
            assertThat(orderStatus(orderId)).isEqualTo("PENDING");
            assertThat(count(ORDER,
                    "SELECT count(*) FROM outbox_events WHERE aggregate_id = ? AND published_at IS NULL",
                    orderId.toString())).isEqualTo(1);
        } finally {
            docker.unpauseContainerCmd(containerId).exec();
        }

        awaitOrderStatus(orderId, "CONFIRMED");
        assertThat(stock(sku)).isEqualTo(2);
    }
}
