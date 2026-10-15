package dev.ronaldofojr.wallet.contracts;

import java.util.UUID;

/** Resposta do account-service a um {@link AccountCommand}. */
public record AccountReply(UUID transferId, CommandType type, boolean success, String reason) {

    public static AccountReply ok(UUID transferId, CommandType type) {
        return new AccountReply(transferId, type, true, null);
    }

    public static AccountReply failed(UUID transferId, CommandType type, String reason) {
        return new AccountReply(transferId, type, false, reason);
    }
}
