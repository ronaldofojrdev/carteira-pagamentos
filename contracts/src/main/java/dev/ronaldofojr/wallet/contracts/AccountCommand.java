package dev.ronaldofojr.wallet.contracts;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Comando enviado pelo transaction-service ao account-service.
 * A dupla (transferId, type) identifica o comando: reenviar a mesma dupla nao pode
 * mexer no saldo duas vezes.
 */
public record AccountCommand(UUID transferId, CommandType type, UUID accountId, BigDecimal amount) {
}
