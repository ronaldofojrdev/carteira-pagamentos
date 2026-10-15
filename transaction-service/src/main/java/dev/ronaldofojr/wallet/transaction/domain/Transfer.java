package dev.ronaldofojr.wallet.transaction.domain;

import dev.ronaldofojr.wallet.contracts.AccountCommand;
import dev.ronaldofojr.wallet.contracts.CommandType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "transfers")
public class Transfer {

    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "from_account_id", nullable = false, updatable = false)
    private UUID fromAccountId;

    @Column(name = "to_account_id", nullable = false, updatable = false)
    private UUID toAccountId;

    @Column(nullable = false, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Transfer() {
    }

    public Transfer(String idempotencyKey, UUID fromAccountId, UUID toAccountId, BigDecimal amount) {
        if (fromAccountId.equals(toAccountId)) {
            throw new InvalidTransferException("Conta de origem e destino devem ser diferentes");
        }
        this.id = UUID.randomUUID();
        this.idempotencyKey = idempotencyKey;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount.setScale(2, RoundingMode.HALF_EVEN);
        this.status = TransferStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /** True se a requisicao tem exatamente os mesmos dados desta transferencia. */
    public boolean hasSameParameters(UUID from, UUID to, BigDecimal value) {
        return fromAccountId.equals(from) && toAccountId.equals(to)
                && amount.compareTo(value.setScale(2, RoundingMode.HALF_EVEN)) == 0;
    }

    /** Comando que a saga espera ver processado no estado atual (vazio em estado final). */
    public Optional<AccountCommand> currentCommand() {
        return switch (status) {
            case PENDING -> Optional.of(new AccountCommand(id, CommandType.DEBIT, fromAccountId, amount));
            case DEBITED -> Optional.of(new AccountCommand(id, CommandType.CREDIT, toAccountId, amount));
            case COMPENSATING -> Optional.of(new AccountCommand(id, CommandType.REFUND, fromAccountId, amount));
            default -> Optional.empty();
        };
    }

    public void markDebited() {
        transition(TransferStatus.PENDING, TransferStatus.DEBITED);
    }

    public void markCompleted() {
        transition(TransferStatus.DEBITED, TransferStatus.COMPLETED);
    }

    public void markFailed(String reason) {
        transition(TransferStatus.PENDING, TransferStatus.FAILED);
        this.failureReason = reason;
    }

    public void startCompensation(String reason) {
        transition(TransferStatus.DEBITED, TransferStatus.COMPENSATING);
        this.failureReason = reason;
    }

    public void markRefunded() {
        transition(TransferStatus.COMPENSATING, TransferStatus.REFUNDED);
    }

    public void markCompensationFailed(String reason) {
        transition(TransferStatus.COMPENSATING, TransferStatus.COMPENSATION_FAILED);
        this.failureReason = this.failureReason + " | estorno falhou: " + reason;
    }

    private void transition(TransferStatus expected, TransferStatus next) {
        if (status != expected) {
            throw new IllegalStateException("Transicao invalida: " + status + " -> " + next);
        }
        this.status = next;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public UUID getFromAccountId() {
        return fromAccountId;
    }

    public UUID getToAccountId() {
        return toAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
