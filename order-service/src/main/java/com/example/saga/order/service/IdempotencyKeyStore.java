package com.example.saga.order.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Persists the Idempotency-Key -> order mapping used by {@code POST /api/orders}. */
@Repository
public class IdempotencyKeyStore {

    public record Entry(String requestHash, UUID orderId) {
    }

    private final JdbcTemplate jdbc;

    public IdempotencyKeyStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Atomically claims the key. If another transaction is claiming the same key right now, this call
     * blocks on the unique index until that transaction ends, and then returns {@code false}.
     *
     * @return true if the caller won the key and must create the order
     */
    public boolean tryRegister(String key, String requestHash, UUID orderId) {
        return jdbc.update("""
                INSERT INTO idempotency_keys (idempotency_key, request_hash, order_id)
                VALUES (?, ?, ?)
                ON CONFLICT (idempotency_key) DO NOTHING
                """, key, requestHash, orderId) == 1;
    }

    public Optional<Entry> find(String key) {
        return jdbc.query(
                        "SELECT request_hash, order_id FROM idempotency_keys WHERE idempotency_key = ?",
                        (rs, i) -> new Entry(rs.getString("request_hash"), rs.getObject("order_id", UUID.class)),
                        key)
                .stream()
                .findFirst();
    }
}
