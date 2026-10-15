package dev.ronaldofojr.wallet.account.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ronaldofojr.wallet.account.IntegrationTestBase;
import dev.ronaldofojr.wallet.account.domain.Account;
import dev.ronaldofojr.wallet.account.repository.AccountRepository;
import dev.ronaldofojr.wallet.account.repository.LedgerEntryRepository;
import dev.ronaldofojr.wallet.contracts.AccountCommand;
import dev.ronaldofojr.wallet.contracts.AccountReply;
import dev.ronaldofojr.wallet.contracts.CommandType;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AccountCommandServiceIT extends IntegrationTestBase {

    @Autowired
    AccountCommandService service;
    @Autowired
    AccountRepository accounts;
    @Autowired
    LedgerEntryRepository ledger;

    private Account newAccount(String balance) {
        return accounts.save(new Account("Teste", new BigDecimal(balance)));
    }

    private BigDecimal balanceOf(Account account) {
        return accounts.findById(account.getId()).orElseThrow().getBalance();
    }

    @Test
    void debitWithEnoughBalanceSucceedsAndWritesLedger() {
        Account account = newAccount("100.00");
        UUID transferId = UUID.randomUUID();

        AccountReply reply = service.process(
                new AccountCommand(transferId, CommandType.DEBIT, account.getId(), new BigDecimal("40.00")));

        assertThat(reply.success()).isTrue();
        assertThat(balanceOf(account)).isEqualByComparingTo("60.00");
        assertThat(ledger.findByAccountIdOrderByCreatedAtDesc(account.getId())).hasSize(1);
    }

    @Test
    void debitWithoutBalanceFailsAndKeepsBalance() {
        Account account = newAccount("10.00");

        AccountReply reply = service.process(
                new AccountCommand(UUID.randomUUID(), CommandType.DEBIT, account.getId(), new BigDecimal("10.01")));

        assertThat(reply.success()).isFalse();
        assertThat(reply.reason()).contains("Saldo insuficiente");
        assertThat(balanceOf(account)).isEqualByComparingTo("10.00");
        assertThat(ledger.findByAccountIdOrderByCreatedAtDesc(account.getId())).isEmpty();
    }

    @Test
    void commandForUnknownAccountFails() {
        AccountReply reply = service.process(
                new AccountCommand(UUID.randomUUID(), CommandType.CREDIT, UUID.randomUUID(), BigDecimal.TEN));

        assertThat(reply.success()).isFalse();
        assertThat(reply.reason()).contains("nao encontrada");
    }

    @Test
    void sameDebitProcessedTwiceOnlyMovesMoneyOnce() {
        Account account = newAccount("100.00");
        AccountCommand command =
                new AccountCommand(UUID.randomUUID(), CommandType.DEBIT, account.getId(), new BigDecimal("30.00"));

        AccountReply first = service.process(command);
        AccountReply second = service.process(command);

        assertThat(first).isEqualTo(second);
        assertThat(balanceOf(account)).isEqualByComparingTo("70.00");
        assertThat(ledger.findByAccountIdOrderByCreatedAtDesc(account.getId())).hasSize(1);
    }

    @Test
    void failedCommandKeepsFailingEvenIfBalanceGrowsBeforeTheRedelivery() {
        Account account = newAccount("10.00");
        AccountCommand command =
                new AccountCommand(UUID.randomUUID(), CommandType.DEBIT, account.getId(), new BigDecimal("50.00"));

        AccountReply first = service.process(command);
        // alguem deposita depois que o debito ja tinha sido recusado
        Account reloaded = accounts.findById(account.getId()).orElseThrow();
        reloaded.credit(new BigDecimal("100.00"));
        accounts.save(reloaded);
        AccountReply second = service.process(command);

        assertThat(first.success()).isFalse();
        assertThat(second).isEqualTo(first);
        assertThat(balanceOf(account)).isEqualByComparingTo("110.00");
    }

    @Test
    void creditAndRefundAddToTheBalance() {
        Account account = newAccount("0.00");
        UUID transferId = UUID.randomUUID();

        service.process(new AccountCommand(transferId, CommandType.CREDIT, account.getId(), new BigDecimal("20.00")));
        service.process(new AccountCommand(transferId, CommandType.REFUND, account.getId(), new BigDecimal("20.00")));

        assertThat(balanceOf(account)).isEqualByComparingTo("40.00");
    }
}
