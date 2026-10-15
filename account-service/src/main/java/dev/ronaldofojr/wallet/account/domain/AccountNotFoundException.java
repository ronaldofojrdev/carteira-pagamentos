package dev.ronaldofojr.wallet.account.domain;

import java.util.UUID;

public class AccountNotFoundException extends BusinessException {

    public AccountNotFoundException(UUID accountId) {
        super("Conta " + accountId + " nao encontrada");
    }
}
