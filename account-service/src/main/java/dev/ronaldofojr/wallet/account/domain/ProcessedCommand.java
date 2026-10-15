package dev.ronaldofojr.wallet.account.domain;

import dev.ronaldofojr.wallet.contracts.AccountReply;
import dev.ronaldofojr.wallet.contracts.CommandType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Registro de um comando ja tratado, com o resultado. Se a mesma mensagem chegar de
 * novo (redelivery do broker, reenvio do orquestrador), devolvemos a resposta guardada
 * sem mexer em saldo.
 */
@Entity
@Table(name = "processed_commands")
public class ProcessedCommand {

    @Id
    private UUID id;

    @Column(name = "transfer_id", nullable = false)
    private UUID transferId;

    @Enumerated(EnumType.STRING)
    @Column(name = "command_type", nullable = false)
    private CommandType commandType;

    @Column(nullable = false)
    private boolean success;

    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProcessedCommand() {
    }

    public ProcessedCommand(UUID transferId, CommandType commandType, boolean success, String reason) {
        this.id = UUID.randomUUID();
        this.transferId = transferId;
        this.commandType = commandType;
        this.success = success;
        this.reason = reason;
        this.createdAt = Instant.now();
    }

    public AccountReply toReply() {
        return new AccountReply(transferId, commandType, success, reason);
    }
}
