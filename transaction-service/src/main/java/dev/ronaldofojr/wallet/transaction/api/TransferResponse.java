package dev.ronaldofojr.wallet.transaction.api;

import dev.ronaldofojr.wallet.transaction.domain.Transfer;
import dev.ronaldofojr.wallet.transaction.domain.TransferStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(UUID id, UUID fromAccountId, UUID toAccountId, BigDecimal amount,
                               TransferStatus status, String failureReason, Instant createdAt, Instant updatedAt) {

    public static TransferResponse from(Transfer t) {
        return new TransferResponse(t.getId(), t.getFromAccountId(), t.getToAccountId(), t.getAmount(),
                t.getStatus(), t.getFailureReason(), t.getCreatedAt(), t.getUpdatedAt());
    }
}
