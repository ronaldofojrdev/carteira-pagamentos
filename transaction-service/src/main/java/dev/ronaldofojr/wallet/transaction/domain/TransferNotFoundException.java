package dev.ronaldofojr.wallet.transaction.domain;

import java.util.UUID;

public class TransferNotFoundException extends RuntimeException {

    public TransferNotFoundException(UUID id) {
        super("Transferencia " + id + " nao encontrada");
    }
}
