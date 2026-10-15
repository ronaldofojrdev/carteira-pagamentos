package dev.ronaldofojr.wallet.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AccountTest {

    @Test
    void debitReducesBalance() {
        Account account = new Account("Maria", new BigDecimal("100.00"));

        account.debit(new BigDecimal("30.50"));

        assertThat(account.getBalance()).isEqualByComparingTo("69.50");
    }

    @Test
    void creditIncreasesBalance() {
        Account account = new Account("Maria", new BigDecimal("10"));

        account.credit(new BigDecimal("5.25"));

        assertThat(account.getBalance()).isEqualByComparingTo("15.25");
    }

    @Test
    void debitCanUseTheWholeBalance() {
        Account account = new Account("Maria", new BigDecimal("10.00"));

        account.debit(new BigDecimal("10.00"));

        assertThat(account.getBalance()).isEqualByComparingTo("0");
    }

    @Test
    void debitAboveBalanceIsRejectedAndBalanceStaysTheSame() {
        Account account = new Account("Maria", new BigDecimal("10.00"));

        assertThatThrownBy(() -> account.debit(new BigDecimal("10.01")))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(account.getBalance()).isEqualByComparingTo("10.00");
    }

    @Test
    void zeroOrNegativeAmountsAreRejected() {
        Account account = new Account("Maria", new BigDecimal("10.00"));

        assertThatThrownBy(() -> account.debit(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.credit(new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void balanceIsKeptWithTwoDecimalPlaces() {
        Account account = new Account("Maria", new BigDecimal("1"));

        assertThat(account.getBalance().scale()).isEqualTo(2);
    }
}
