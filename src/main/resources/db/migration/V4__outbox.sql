-- Transactional outbox: messages to publish are written in the same transaction as the data they describe.
-- A relay sends them to Kafka afterwards, so a crash between commit and send cannot lose a message.

CREATE TABLE outbox_event (
    id             BIGSERIAL PRIMARY KEY,
    topic          VARCHAR(100) NOT NULL,
    message_key    VARCHAR(100) NOT NULL,
    payload        VARCHAR(200) NOT NULL,
    correlation_id VARCHAR(64),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at        TIMESTAMPTZ
);

-- The relay only ever looks for unsent rows, so index just those
CREATE INDEX idx_outbox_unsent ON outbox_event (id) WHERE sent_at IS NULL;
