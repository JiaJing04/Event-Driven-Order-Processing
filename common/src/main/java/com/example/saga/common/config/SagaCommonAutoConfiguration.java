package com.example.saga.common.config;

import com.example.saga.common.events.Topics;
import com.example.saga.common.idempotency.ProcessedMessageStore;
import com.example.saga.common.kafka.KafkaEvents;
import com.example.saga.common.kafka.MalformedEventException;
import com.example.saga.common.outbox.OutboxRelay;
import com.example.saga.common.outbox.OutboxService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Wires the saga infrastructure into every service that has {@code saga-common} on its classpath.
 * Registered through {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 */
@AutoConfiguration(after = {JacksonAutoConfiguration.class, JdbcTemplateAutoConfiguration.class, KafkaAutoConfiguration.class})
@EnableScheduling
@EnableConfigurationProperties(SagaProperties.class)
public class SagaCommonAutoConfiguration {

    @Bean
    public OutboxService outboxService(JdbcTemplate jdbc, ObjectMapper mapper) {
        return new OutboxService(jdbc, mapper);
    }

    @Bean
    public OutboxRelay outboxRelay(JdbcTemplate jdbc,
                                   KafkaTemplate<String, String> kafka,
                                   PlatformTransactionManager transactionManager,
                                   SagaProperties properties) {
        return new OutboxRelay(jdbc, kafka, transactionManager, properties);
    }

    @Bean
    public ProcessedMessageStore processedMessageStore(JdbcTemplate jdbc) {
        return new ProcessedMessageStore(jdbc);
    }

    @Bean
    public KafkaEvents kafkaEvents(ObjectMapper mapper) {
        return new KafkaEvents(mapper);
    }

    /**
     * Consumer error handling: a failing record is retried a few times (blocking the partition, which
     * preserves ordering), then published to {@code <topic>.DLT} and skipped. Spring Boot picks up this
     * bean and applies it to every {@code @KafkaListener}.
     */
    @Bean
    public DefaultErrorHandler sagaKafkaErrorHandler(KafkaTemplate<String, String> kafka, SagaProperties properties) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafka,
                // partition -1: let the producer choose, so the DLT does not need the same partition count
                (record, ex) -> new TopicPartition(Topics.deadLetter(record.topic()), -1));
        SagaProperties.Consumer consumer = properties.consumer();
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer,
                new FixedBackOff(consumer.retryBackoffMs(), consumer.retryAttempts()));
        handler.addNotRetryableExceptions(MalformedEventException.class);
        return handler;
    }

    @Bean
    public KafkaAdmin.NewTopics sagaTopics(SagaProperties properties) {
        SagaProperties.Topics t = properties.topics();
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(Topics.ORDER_EVENTS).partitions(t.partitions()).replicas(t.replicas()).build(),
                TopicBuilder.name(Topics.INVENTORY_EVENTS).partitions(t.partitions()).replicas(t.replicas()).build(),
                TopicBuilder.name(Topics.PAYMENT_EVENTS).partitions(t.partitions()).replicas(t.replicas()).build());
    }
}
