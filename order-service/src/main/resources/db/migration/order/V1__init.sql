-- ---------------------------------------------------------------- domain
CREATE TABLE orders (
    id             UUID           PRIMARY KEY,
    customer_id    VARCHAR(100)   NOT NULL,
    status         VARCHAR(20)    NOT NULL,
    total_amount   NUMERIC(19, 2) NOT NULL,
    failure_reason VARCHAR(500),
    created_at     TIMESTAMPTZ    NOT NULL,
    updated_at     TIMESTAMPTZ    NOT NULL,
    version        BIGINT         NOT NULL DEFAULT 0
);

CREATE TABLE order_items (
    order_id   UUID           NOT NULL REFERENCES orders (id),
    line_no    INTEGER        NOT NULL,
    sku        VARCHAR(100)   NOT NULL,
    quantity   INTEGER        NOT NULL,
    unit_price NUMERIC(19, 2) NOT NULL,
    PRIMARY KEY (order_id, line_no)
);

CREATE INDEX idx_orders_customer ON orders (customer_id);

-- ------------------------------------------------ HTTP idempotency (Idempotency-Key header)
CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(200) PRIMARY KEY,
    request_hash    VARCHAR(64)  NOT NULL,
    order_id        UUID         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------- transactional outbox
CREATE TABLE outbox_events (
    id             BIGSERIAL    PRIMARY KEY,
    event_id       UUID         NOT NULL UNIQUE,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   VARCHAR(100) NOT NULL,
    topic          VARCHAR(200) NOT NULL,
    message_key    VARCHAR(200) NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        TEXT         NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (id) WHERE published_at IS NULL;

-- ---------------------------------------------------------------- idempotent consumer (inbox)
CREATE TABLE processed_messages (
    consumer     VARCHAR(100) NOT NULL,
    event_id     UUID         NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer, event_id)
);
