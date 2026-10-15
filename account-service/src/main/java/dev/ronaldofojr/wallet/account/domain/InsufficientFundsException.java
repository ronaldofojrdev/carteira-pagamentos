package dev.ronaldofojr.wallet.account.domain;

import java.util.UUID;

public class InsufficientFundsException extends BusinessException {

    public InsufficientFundsException(UUID accountId) {
        super("Saldo insuficiente na conta " + accountId);
    }
}
