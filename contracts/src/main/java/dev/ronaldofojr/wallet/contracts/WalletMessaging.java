package dev.ronaldofojr.wallet.contracts;

/**
 * Nomes de exchanges, filas e routing keys. Ficam num lugar so para os dois
 * servicos nao divergirem.
 */
public final class WalletMessaging {

    public static final String COMMANDS_EXCHANGE = "wallet.commands";
    public static final String REPLIES_EXCHANGE = "wallet.replies";

    public static final String ACCOUNT_COMMANDS_QUEUE = "account.commands";
    public static final String ACCOUNT_COMMANDS_DLQ = "account.commands.dlq";

    public static final String TRANSFER_REPLIES_QUEUE = "transfer.replies";
    public static final String TRANSFER_REPLIES_DLQ = "transfer.replies.dlq";

    public static final String ACCOUNT_COMMAND_KEY = "account.command";
    public static final String ACCOUNT_REPLY_KEY = "account.reply";

    private WalletMessaging() {
    }
}
