-- ---------------------------------------------------------------- domain
CREATE TABLE stock_items (
    sku                VARCHAR(100) PRIMARY KEY,
    available_quantity INTEGER      NOT NULL CHECK (available_quantity >= 0)
);

CREATE TABLE reservations (
    order_id   UUID        PRIMARY KEY,
    status     VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version    BIGINT      NOT NULL DEFAULT 0
);

CREATE TABLE reservation_items (
    order_id UUID         NOT NULL REFERENCES reservations (order_id),
    line_no  INTEGER      NOT NULL,
    sku      VARCHAR(100) NOT NULL,
    quantity INTEGER      NOT NULL,
    PRIMARY KEY (order_id, line_no)
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
