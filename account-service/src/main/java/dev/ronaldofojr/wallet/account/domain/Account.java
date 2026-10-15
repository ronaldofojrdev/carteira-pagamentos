package dev.ronaldofojr.wallet.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    private UUID id;

    @Column(name = "owner_name", nullable = false)
    private String ownerName;

    @Column(nullable = false)
    private BigDecimal balance;

    // lock otimista: dois debitos concorrentes na mesma conta nao se atropelam
    @Version
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Account() {
    }

    public Account(String ownerName, BigDecimal initialBalance) {
        this.id = UUID.randomUUID();
        this.ownerName = ownerName;
        this.balance = normalize(initialBalance);
        this.createdAt = Instant.now();
    }

    public void debit(BigDecimal amount) {
        BigDecimal value = requirePositive(amount);
        if (balance.compareTo(value) < 0) {
            throw new InsufficientFundsException(id);
        }
        balance = balance.subtract(value);
    }

    public void credit(BigDecimal amount) {
        balance = balance.add(requirePositive(amount));
    }

    private static BigDecimal requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("O valor deve ser maior que zero");
        }
        return normalize(amount);
    }

    private static BigDecimal normalize(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_EVEN);
    }

    public UUID getId() {
        return id;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
