package dev.ronaldofojr.wallet.transaction.repository;

import dev.ronaldofojr.wallet.transaction.domain.Transfer;
import dev.ronaldofojr.wallet.transaction.domain.TransferStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);

    long countByStatusIn(Collection<TransferStatus> statuses);

    List<Transfer> findByStatusInAndUpdatedAtBefore(Collection<TransferStatus> statuses, Instant limit);
}
