package dev.ronaldofojr.wallet.account.repository;

import dev.ronaldofojr.wallet.account.domain.LedgerEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(UUID accountId);

    Page<LedgerEntry> findByAccountId(UUID accountId, Pageable pageable);
}
