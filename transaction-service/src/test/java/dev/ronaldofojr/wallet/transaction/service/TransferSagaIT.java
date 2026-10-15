package dev.ronaldofojr.wallet.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.ronaldofojr.wallet.contracts.AccountCommand;
import dev.ronaldofojr.wallet.contracts.AccountReply;
import dev.ronaldofojr.wallet.contracts.CommandType;
import dev.ronaldofojr.wallet.contracts.WalletMessaging;
import dev.ronaldofojr.wallet.transaction.IntegrationTestBase;
import dev.ronaldofojr.wallet.transaction.domain.TransferStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A saga inteira passando pelo broker: o teste faz o papel do account-service,
 * lendo cada comando da fila e respondendo na exchange de replies.
 */
class TransferSagaIT extends IntegrationTestBase {

    @Autowired
    TransferService service;

    private final UUID from = UUID.randomUUID();
    private final UUID to = UUID.randomUUID();

    private void reply(AccountReply reply) {
        rabbitTemplate.convertAndSend(WalletMessaging.REPLIES_EXCHANGE, WalletMessaging.ACCOUNT_REPLY_KEY, reply);
    }

    private void awaitStatus(UUID id, TransferStatus expected) {
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(service.get(id).getStatus()).isEqualTo(expected));
    }

    @Test
    void happyPathEndsCompleted() {
        UUID id = service.create("saga-1", from, to, new BigDecimal("100")).transfer().getId();

        AccountCommand debit = nextCommand();
        assertThat(debit.type()).isEqualTo(CommandType.DEBIT);
        assertThat(debit.accountId()).isEqualTo(from);
        reply(AccountReply.ok(id, CommandType.DEBIT));
        awaitStatus(id, TransferStatus.DEBITED);

        AccountCommand credit = nextCommand();
        assertThat(credit.type()).isEqualTo(CommandType.CREDIT);
        assertThat(credit.accountId()).isEqualTo(to);
        reply(AccountReply.ok(id, CommandType.CREDIT));
        awaitStatus(id, TransferStatus.COMPLETED);

        assertThat(nextCommand()).as("saga concluida nao envia mais nada").isNull();
    }

    @Test
    void refusedDebitEndsFailedWithoutCreditOrRefund() {
        UUID id = service.create("saga-2", from, to, new BigDecimal("100")).transfer().getId();
        nextCommand();

        reply(AccountReply.failed(id, CommandType.DEBIT, "Saldo insuficiente na conta " + from));

        awaitStatus(id, TransferStatus.FAILED);
        assertThat(service.get(id).getFailureReason()).contains("Saldo insuficiente");
        assertThat(nextCommand()).isNull();
    }

    @Test
    void refusedCreditTriggersRefundToTheOrigin() {
        UUID id = service.create("saga-3", from, to, new BigDecimal("100")).transfer().getId();
        nextCommand();
        reply(AccountReply.ok(id, CommandType.DEBIT));
        awaitStatus(id, TransferStatus.DEBITED);
        assertThat(nextCommand().type()).isEqualTo(CommandType.CREDIT);

        reply(AccountReply.failed(id, CommandType.CREDIT, "Conta " + to + " nao encontrada"));
        awaitStatus(id, TransferStatus.COMPENSATING);

        AccountCommand refund = nextCommand();
        assertThat(refund.type()).isEqualTo(CommandType.REFUND);
        assertThat(refund.accountId()).isEqualTo(from);
        assertThat(refund.amount()).isEqualByComparingTo("100");

        reply(AccountReply.ok(id, CommandType.REFUND));
        awaitStatus(id, TransferStatus.REFUNDED);
        assertThat(service.get(id).getFailureReason()).contains("nao encontrada");
    }

    @Test
    void refusedRefundIsMarkedForManualAction() {
        UUID id = service.create("saga-4", from, to, new BigDecimal("100")).transfer().getId();
        nextCommand();
        reply(AccountReply.ok(id, CommandType.DEBIT));
        awaitStatus(id, TransferStatus.DEBITED);
        nextCommand();
        reply(AccountReply.failed(id, CommandType.CREDIT, "destino inexistente"));
        awaitStatus(id, TransferStatus.COMPENSATING);
        nextCommand();

        reply(AccountReply.failed(id, CommandType.REFUND, "origem bloqueada"));

        awaitStatus(id, TransferStatus.COMPENSATION_FAILED);
        assertThat(service.get(id).getFailureReason()).contains("destino inexistente").contains("origem bloqueada");
    }
}
