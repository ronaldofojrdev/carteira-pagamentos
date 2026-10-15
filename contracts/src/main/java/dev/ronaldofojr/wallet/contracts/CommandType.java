package dev.ronaldofojr.wallet.contracts;

public enum CommandType {
    /** Tira o valor da conta de origem. */
    DEBIT,
    /** Coloca o valor na conta de destino. */
    CREDIT,
    /** Compensacao: devolve o valor para a conta de origem quando o credito falha. */
    REFUND
}
