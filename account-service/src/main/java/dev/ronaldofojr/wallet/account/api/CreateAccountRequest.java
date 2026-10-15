package dev.ronaldofojr.wallet.account.api;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateAccountRequest(
        @NotBlank @Size(max = 120) String ownerName,
        @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal initialBalance) {
}
