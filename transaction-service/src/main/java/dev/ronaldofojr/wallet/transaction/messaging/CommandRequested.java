package dev.ronaldofojr.wallet.transaction.messaging;

import dev.ronaldofojr.wallet.contracts.AccountCommand;

/** Evento interno: "este comando deve ser enviado quando a transacao do banco commitar". */
public record CommandRequested(AccountCommand command) {
}
