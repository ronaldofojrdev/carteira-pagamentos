package dev.ronaldofojr.wallet.account.repository;

import dev.ronaldofojr.wallet.account.domain.ProcessedCommand;
import dev.ronaldofojr.wallet.contracts.CommandType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedCommandRepository extends JpaRepository<ProcessedCommand, UUID> {

    Optional<ProcessedCommand> findByTransferIdAndCommandType(UUID transferId, CommandType commandType);
}
