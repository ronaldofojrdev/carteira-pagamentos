package dev.ronaldofojr.wallet.account.repository;

import dev.ronaldofojr.wallet.account.domain.Account;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {
}
