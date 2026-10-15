package dev.ronaldofojr.wallet.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ronaldofojr.wallet.contracts.AccountCommand;
import dev.ronaldofojr.wallet.contracts.AccountReply;
import dev.ronaldofojr.wallet.contracts.CommandType;
import dev.ronaldofojr.wallet.transaction.IntegrationTestBase;
import dev.ronaldofojr.wallet.transaction.domain.IdempotencyConflictException;
import dev.ronaldofojr.wallet.transaction.domain.InvalidTransferException;
import dev.ronaldofojr.wallet.transaction.domain.TransferStatus;
import dev.ronaldofojr.wallet.transaction.service.TransferService.CreationResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class TransferServiceIT extends IntegrationTestBase {

    @Autowired
    TransferService service;
    @Autowired
    StuckTransferRetrier retrier;
    @Autowired
    JdbcTemplate jdbc;

    private final UUID from = UUID.randomUUID();
    private final UUID to = UUID.randomUUID();

    @Test
    void createPersistsPendingTransferAndPublishesTheDebit() {
        CreationResult result = service.create("key-a", from, to, new BigDecimal("75.00"));

        assertThat(result.created()).isTrue();
        assertThat(result.transfer().getStatus()).isEqualTo(TransferStatus.PENDING);

        AccountCommand command = nextCommand();
        assertThat(command).isNotNull();
        assertThat(command.type()).isEqualTo(CommandType.DEBIT);
        assertThat(command.accountId()).isEqualTo(from);
        assertThat(command.transferId()).isEqualTo(result.transfer().getId());
        assertThat(command.amount()).isEqualByComparingTo("75.00");
    }

    @Test
    void sameIdempotencyKeyAndDataReturnsTheSameTransferWithoutANewCommand() {
        CreationResult first = service.create("key-b", from, to, new BigDecimal("10"));
        CreationResult second = service.create("key-b", from, to, new BigDecimal("10.00"));

        assertThat(second.created()).isFalse();
        assertThat(second.transfer().getId()).isEqualTo(first.transfer().getId());
        assertThat(transfers.count()).isEqualTo(1);

        assertThat(nextCommand()).isNotNull();
        assertThat(nextCommand()).as("so um comando deve ter sido publicado").isNull();
    }

    @Test
    void sameIdempotencyKeyWithDifferentDataIsAConflict() {
        service.create("key-c", from, to, new BigDecimal("10"));

        assertThatThrownBy(() -> service.create("key-c", from, to, new BigDecimal("11")))
                .isInstanceOf(IdempotencyConflictException.class);
        assertThat(transfers.count()).isEqualTo(1);
    }

    @Test
    void concurrentRequestsWithTheSameKeyCreateOnlyOneTransfer() throws Exception {
        var pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        try {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<CreationResult>>();
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> service.create("key-race", from, to, new BigDecimal("5"))));
            }
            var ids = new java.util.HashSet<UUID>();
            for (var f : futures) {
                ids.add(f.get().transfer().getId());
            }
            assertThat(ids).hasSize(1);
            assertThat(transfers.count()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void sameOriginAndDestinationIsRejectedBeforeTouchingTheBroker() {
        assertThatThrownBy(() -> service.create("key-d", from, from, BigDecimal.TEN))
                .isInstanceOf(InvalidTransferException.class);

        assertThat(transfers.count()).isZero();
        assertThat(nextCommand()).isNull();
    }

    @Test
    void duplicatedReplyDoesNotAdvanceTheSagaTwice() {
        UUID id = service.create("key-e", from, to, new BigDecimal("20")).transfer().getId();
        assertThat(nextCommand().type()).isEqualTo(CommandType.DEBIT);

        AccountReply debitOk = AccountReply.ok(id, CommandType.DEBIT);
        service.handleReply(debitOk);
        service.handleReply(debitOk);

        assertThat(service.get(id).getStatus()).isEqualTo(TransferStatus.DEBITED);
        assertThat(nextCommand().type()).isEqualTo(CommandType.CREDIT);
        assertThat(nextCommand()).as("o CREDIT nao pode ser publicado duas vezes").isNull();
    }

    @Test
    void replyForUnknownTransferIsIgnored() {
        service.handleReply(AccountReply.ok(UUID.randomUUID(), CommandType.DEBIT));

        assertThat(transfers.count()).isZero();
    }

    @Test
    void retrierResendsTheCommandOfAStuckTransfer() {
        UUID id = service.create("key-f", from, to, new BigDecimal("30")).transfer().getId();
        assertThat(nextCommand()).isNotNull();

        // simula transferencia parada ha 10 minutos (ex: broker estava fora do ar)
        jdbc.update("UPDATE transfers SET updated_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minus(10, ChronoUnit.MINUTES)), id);

        retrier.resendStuckTransfers();

        AccountCommand resent = nextCommand();
        assertThat(resent).isNotNull();
        assertThat(resent.type()).isEqualTo(CommandType.DEBIT);
        assertThat(resent.transferId()).isEqualTo(id);
    }

    @Test
    void retrierIgnoresRecentAndFinishedTransfers() {
        UUID recent = service.create("key-g", from, to, new BigDecimal("1")).transfer().getId();
        UUID finished = service.create("key-h", from, to, new BigDecimal("2")).transfer().getId();
        nextCommand();
        nextCommand();
        service.handleReply(AccountReply.failed(finished, CommandType.DEBIT, "Saldo insuficiente"));
        jdbc.update("UPDATE transfers SET updated_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minus(10, ChronoUnit.MINUTES)), finished);

        retrier.resendStuckTransfers();

        assertThat(nextCommand()).as("nada para reenviar").isNull();
        assertThat(service.get(recent).getStatus()).isEqualTo(TransferStatus.PENDING);
    }
}
