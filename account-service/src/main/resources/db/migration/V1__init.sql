CREATE TABLE accounts (
    id          UUID PRIMARY KEY,
    owner_name  VARCHAR(120)   NOT NULL,
    balance     NUMERIC(19, 2) NOT NULL,
    version     BIGINT         NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ    NOT NULL,
    CONSTRAINT accounts_balance_non_negative CHECK (balance >= 0)
);

-- historico de movimentacoes (extrato)
CREATE TABLE ledger_entries (
    id             UUID PRIMARY KEY,
    account_id     UUID           NOT NULL REFERENCES accounts (id),
    transfer_id    UUID           NOT NULL,
    type           VARCHAR(10)    NOT NULL,
    amount         NUMERIC(19, 2) NOT NULL,
    balance_after  NUMERIC(19, 2) NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_ledger_account ON ledger_entries (account_id, created_at DESC);

-- guarda o resultado de cada comando ja tratado: e isso que garante a idempotencia
CREATE TABLE processed_commands (
    id           UUID PRIMARY KEY,
    transfer_id  UUID         NOT NULL,
    command_type VARCHAR(10)  NOT NULL,
    success      BOOLEAN      NOT NULL,
    reason       VARCHAR(255),
    created_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_processed_transfer_type UNIQUE (transfer_id, command_type)
);
