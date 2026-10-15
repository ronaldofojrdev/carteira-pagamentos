package dev.ronaldofojr.wallet.transaction.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ronaldofojr.wallet.contracts.AccountCommand;
import dev.ronaldofojr.wallet.contracts.CommandType;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransferTest {

    private final UUID from = UUID.randomUUID();
    private final UUID to = UUID.randomUUID();

    private Transfer newTransfer() {
        return new Transfer("key-1", from, to, new BigDecimal("50"));
    }

    @Test
    void startsPendingAndWaitsForTheDebit() {
        Transfer transfer = newTransfer();

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.PENDING);
        assertThat(transfer.currentCommand()).contains(
                new AccountCommand(transfer.getId(), CommandType.DEBIT, from, new BigDecimal("50.00")));
    }

    @Test
    void happyPathWalksThroughDebitAndCredit() {
        Transfer transfer = newTransfer();

        transfer.markDebited();
        assertThat(transfer.currentCommand().orElseThrow().type()).isEqualTo(CommandType.CREDIT);
        assertThat(transfer.currentCommand().orElseThrow().accountId()).isEqualTo(to);

        transfer.markCompleted();
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(transfer.currentCommand()).isEmpty();
    }

    @Test
    void failedDebitEndsAsFailedWithoutCompensation() {
        Transfer transfer = newTransfer();

        transfer.markFailed("Saldo insuficiente");

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(transfer.getFailureReason()).isEqualTo("Saldo insuficiente");
        assertThat(transfer.currentCommand()).isEmpty();
    }

    @Test
    void failedCreditStartsCompensationRefundingTheOrigin() {
        Transfer transfer = newTransfer();
        transfer.markDebited();

        transfer.startCompensation("Conta de destino nao existe");

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPENSATING);
        AccountCommand refund = transfer.currentCommand().orElseThrow();
        assertThat(refund.type()).isEqualTo(CommandType.REFUND);
        assertThat(refund.accountId()).isEqualTo(from);

        transfer.markRefunded();
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.REFUNDED);
        assertThat(transfer.getStatus().isFinal()).isTrue();
    }

    @Test
    void illegalTransitionsAreRejected() {
        Transfer transfer = newTransfer();

        assertThatThrownBy(transfer::markCompleted).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(transfer::markRefunded).isInstanceOf(IllegalStateException.class);

        transfer.markFailed("x");
        assertThatThrownBy(transfer::markDebited).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sameOriginAndDestinationIsRejected() {
        assertThatThrownBy(() -> new Transfer("key", from, from, BigDecimal.TEN))
                .isInstanceOf(InvalidTransferException.class);
    }

    @Test
    void comparesParametersIgnoringTrailingZeros() {
        Transfer transfer = newTransfer();

        assertThat(transfer.hasSameParameters(from, to, new BigDecimal("50.0"))).isTrue();
        assertThat(transfer.hasSameParameters(from, to, new BigDecimal("50.01"))).isFalse();
        assertThat(transfer.hasSameParameters(to, from, new BigDecimal("50"))).isFalse();
    }
}
