CREATE TABLE transfers (
    id               UUID PRIMARY KEY,
    idempotency_key  VARCHAR(100)   NOT NULL,
    from_account_id  UUID           NOT NULL,
    to_account_id    UUID           NOT NULL,
    amount           NUMERIC(19, 2) NOT NULL,
    status           VARCHAR(30)    NOT NULL,
    failure_reason   VARCHAR(255),
    version          BIGINT         NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ    NOT NULL,
    updated_at       TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uk_transfers_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT transfers_amount_positive CHECK (amount > 0),
    CONSTRAINT transfers_different_accounts CHECK (from_account_id <> to_account_id)
);

CREATE INDEX idx_transfers_status_updated ON transfers (status, updated_at);
