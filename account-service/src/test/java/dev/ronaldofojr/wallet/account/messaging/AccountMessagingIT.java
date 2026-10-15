package dev.ronaldofojr.wallet.account.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ronaldofojr.wallet.account.IntegrationTestBase;
import dev.ronaldofojr.wallet.account.domain.Account;
import dev.ronaldofojr.wallet.account.repository.AccountRepository;
import dev.ronaldofojr.wallet.contracts.AccountCommand;
import dev.ronaldofojr.wallet.contracts.AccountReply;
import dev.ronaldofojr.wallet.contracts.CommandType;
import dev.ronaldofojr.wallet.contracts.WalletMessaging;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;

/** Passa pelo RabbitMQ de verdade: manda o comando, le a resposta na fila de replies. */
class AccountMessagingIT extends IntegrationTestBase {

    private static final ParameterizedTypeReference<AccountReply> REPLY_TYPE = new ParameterizedTypeReference<>() {
    };

    @Autowired
    RabbitTemplate rabbitTemplate;
    @Autowired
    AmqpAdmin amqpAdmin;
    @Autowired
    AccountRepository accounts;

    private void send(AccountCommand command) {
        rabbitTemplate.convertAndSend(WalletMessaging.COMMANDS_EXCHANGE, WalletMessaging.ACCOUNT_COMMAND_KEY, command);
    }

    private AccountReply nextReply() {
        return rabbitTemplate.receiveAndConvert(WalletMessaging.TRANSFER_REPLIES_QUEUE, 10_000, REPLY_TYPE);
    }

    @Test
    void debitCommandIsProcessedAndRepliedThroughTheBroker() {
        amqpAdmin.purgeQueue(WalletMessaging.TRANSFER_REPLIES_QUEUE, true);
        Account account = accounts.save(new Account("Ana", new BigDecimal("100.00")));
        UUID transferId = UUID.randomUUID();

        send(new AccountCommand(transferId, CommandType.DEBIT, account.getId(), new BigDecimal("25.00")));

        AccountReply reply = nextReply();
        assertThat(reply).isNotNull();
        assertThat(reply.transferId()).isEqualTo(transferId);
        assertThat(reply.type()).isEqualTo(CommandType.DEBIT);
        assertThat(reply.success()).isTrue();
        assertThat(accounts.findById(account.getId()).orElseThrow().getBalance()).isEqualByComparingTo("75.00");
    }

    @Test
    void duplicatedMessageDebitsOnlyOnceButIsAnsweredTwice() {
        amqpAdmin.purgeQueue(WalletMessaging.TRANSFER_REPLIES_QUEUE, true);
        Account account = accounts.save(new Account("Bia", new BigDecimal("100.00")));
        AccountCommand command =
                new AccountCommand(UUID.randomUUID(), CommandType.DEBIT, account.getId(), new BigDecimal("60.00"));

        send(command);
        send(command);

        AccountReply first = nextReply();
        AccountReply second = nextReply();
        assertThat(first).isNotNull().isEqualTo(second);
        assertThat(first.success()).isTrue();
        assertThat(accounts.findById(account.getId()).orElseThrow().getBalance()).isEqualByComparingTo("40.00");
    }

    @Test
    void debitAboveTheBalanceGetsAFailedReplyAndKeepsTheBalance() {
        amqpAdmin.purgeQueue(WalletMessaging.TRANSFER_REPLIES_QUEUE, true);
        Account account = accounts.save(new Account("Caio", new BigDecimal("10.00")));
        UUID transferId = UUID.randomUUID();

        send(new AccountCommand(transferId, CommandType.DEBIT, account.getId(), new BigDecimal("10.01")));

        AccountReply reply = nextReply();
        assertThat(reply).isNotNull();
        assertThat(reply.success()).isFalse();
        assertThat(reply.reason()).contains("Saldo insuficiente");
        assertThat(accounts.findById(account.getId()).orElseThrow().getBalance()).isEqualByComparingTo("10.00");
    }

    @Test
    void debitOfTheExactBalanceIsAccepted() {
        amqpAdmin.purgeQueue(WalletMessaging.TRANSFER_REPLIES_QUEUE, true);
        Account account = accounts.save(new Account("Duda", new BigDecimal("10.00")));

        send(new AccountCommand(UUID.randomUUID(), CommandType.DEBIT, account.getId(), new BigDecimal("10.00")));

        assertThat(nextReply().success()).isTrue();
        assertThat(accounts.findById(account.getId()).orElseThrow().getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void commandForAnAccountThatDoesNotExistGetsAFailedReply() {
        amqpAdmin.purgeQueue(WalletMessaging.TRANSFER_REPLIES_QUEUE, true);
        UUID missing = UUID.randomUUID();

        send(new AccountCommand(UUID.randomUUID(), CommandType.CREDIT, missing, new BigDecimal("5.00")));

        AccountReply reply = nextReply();
        assertThat(reply).isNotNull();
        assertThat(reply.type()).isEqualTo(CommandType.CREDIT);
        assertThat(reply.success()).isFalse();
        assertThat(reply.reason()).contains("nao encontrada").contains(missing.toString());
    }

    @Test
    void aRefusedDebitIsAnsweredTheSameWayWhenTheMessageIsRedelivered() {
        amqpAdmin.purgeQueue(WalletMessaging.TRANSFER_REPLIES_QUEUE, true);
        Account account = accounts.save(new Account("Edu", new BigDecimal("1.00")));
        AccountCommand command = new AccountCommand(UUID.randomUUID(), CommandType.DEBIT, account.getId(), new BigDecimal("50.00"));

        send(command);
        send(command);

        AccountReply first = nextReply();
        AccountReply second = nextReply();
        assertThat(first.success()).isFalse();
        assertThat(second).isEqualTo(first);
    }
}
