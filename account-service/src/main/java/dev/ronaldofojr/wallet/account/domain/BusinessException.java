package dev.ronaldofojr.wallet.account.domain;

/** Erro de regra de negocio esperado (diferente de um bug ou falha de infra). */
public abstract class BusinessException extends RuntimeException {

    protected BusinessException(String message) {
        super(message);
    }
}
