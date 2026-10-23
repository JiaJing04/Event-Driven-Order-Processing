package com.example.saga.it;

import com.example.saga.inventory.InventoryServiceApplication;
import com.example.saga.order.OrderServiceApplication;
import com.example.saga.payment.PaymentServiceApplication;
import org.springframework.boot.Banner;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Process-wide singleton test environment: one PostgreSQL container (three databases), one Kafka broker and
 * the three real Spring Boot applications running in this JVM, each with its own ApplicationContext, HTTP port,
 * database and Kafka consumer group - just like in production, only without separate processes.
 * <p>
 * It is started lazily the first time any test touches this class and torn down by a shutdown hook
 * (Testcontainers' Ryuk sidecar removes the containers even if the JVM is killed).
 * <p>
 * Note on configuration: all three jars contain an {@code application.yml} and only the first one on the
 * classpath is visible to Spring. That is fine here because every service-specific value is passed
 * explicitly as a command-line argument below (highest precedence).
 */
final class SagaEnvironment {

    static final String DB_USER = "saga";
    static final String DB_PASSWORD = "saga";

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withUsername(DB_USER)
            .withPassword(DB_PASSWORD)
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("init-databases.sql"),
                    "/docker-entrypoint-initdb.d/init-databases.sql");

    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.1"));

    static final RunningService ORDER;
    static final RunningService INVENTORY;
    static final RunningService PAYMENT;

    /** A booted service. */
    record RunningService(String name, String database, ConfigurableApplicationContext context, int port) {
        String baseUrl() {
            return "http://localhost:" + port;
        }

        String jdbcUrl() {
            return SagaEnvironment.jdbcUrl(database);
        }
    }

    static {
        POSTGRES.start();
        KAFKA.start();

        ORDER = boot("order-service", "order_db", "order", OrderServiceApplication.class);
        INVENTORY = boot("inventory-service", "inventory_db", "inventory", InventoryServiceApplication.class);
        PAYMENT = boot("payment-service", "payment_db", "payment", PaymentServiceApplication.class);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (RunningService service : new RunningService[]{ORDER, INVENTORY, PAYMENT}) {
                try {
                    service.context().close();
                } catch (RuntimeException ignored) {
                    // best effort
                }
            }
        }, "saga-it-shutdown"));
    }

    private SagaEnvironment() {
    }

    static String jdbcUrl(String database) {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + database;
    }

    private static RunningService boot(String name, String database, String migrationDir, Class<?> application) {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(application)
                .bannerMode(Banner.Mode.OFF)
                .run(
                        "--spring.application.name=" + name,
                        "--server.port=0",
                        "--spring.datasource.url=" + jdbcUrl(database),
                        "--spring.datasource.username=" + DB_USER,
                        "--spring.datasource.password=" + DB_PASSWORD,
                        "--spring.datasource.hikari.maximum-pool-size=5",
                        "--spring.flyway.locations=classpath:db/migration/" + migrationDir,
                        "--spring.kafka.bootstrap-servers=" + KAFKA.getBootstrapServers(),
                        // make the tests quick
                        "--saga.outbox.poll-interval-ms=100",
                        "--saga.consumer.retry-backoff-ms=200");
        int port = Integer.parseInt(context.getEnvironment().getProperty("local.server.port"));
        return new RunningService(name, database, context, port);
    }
}
