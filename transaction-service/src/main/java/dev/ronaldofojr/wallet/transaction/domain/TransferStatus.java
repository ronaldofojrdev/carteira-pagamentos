package dev.ronaldofojr.wallet.transaction.domain;

/**
 * Estados da saga:
 *
 * PENDING -> DEBITED -> COMPLETED
 * PENDING -> FAILED                      (debito recusado, nada a desfazer)
 * DEBITED -> COMPENSATING -> REFUNDED    (credito recusado, valor devolvido a origem)
 * COMPENSATING -> COMPENSATION_FAILED    (estorno recusado, precisa de acao manual)
 */
public enum TransferStatus {
    PENDING,
    DEBITED,
    COMPLETED,
    FAILED,
    COMPENSATING,
    REFUNDED,
    COMPENSATION_FAILED;

    public boolean isFinal() {
        return this == COMPLETED || this == FAILED || this == REFUNDED || this == COMPENSATION_FAILED;
    }
}
