package dev.ronaldofojr.wallet.transaction.domain;

public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String key) {
        super("A Idempotency-Key '" + key + "' ja foi usada com dados diferentes");
    }
}
