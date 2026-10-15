package dev.ronaldofojr.wallet.account.api;

import dev.ronaldofojr.wallet.account.domain.Account;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(UUID id, String ownerName, BigDecimal balance, Instant createdAt) {

    public static AccountResponse from(Account a) {
        return new AccountResponse(a.getId(), a.getOwnerName(), a.getBalance(), a.getCreatedAt());
    }
}
