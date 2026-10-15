package dev.ronaldofojr.wallet.transaction.service;

import dev.ronaldofojr.wallet.transaction.domain.Transfer;
import dev.ronaldofojr.wallet.transaction.domain.TransferStatus;
import dev.ronaldofojr.wallet.transaction.messaging.CommandPublisher;
import dev.ronaldofojr.wallet.transaction.repository.TransferRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Rede de seguranca da saga: se uma transferencia ficou parada em PENDING, DEBITED ou
 * COMPENSATING por mais de N segundos (mensagem perdida, broker fora do ar no momento
 * do envio), reenvia o comando daquele passo. Reenviar e seguro porque o
 * account-service e idempotente.
 */
@Component
public class StuckTransferRetrier {

    private static final Logger log = LoggerFactory.getLogger(StuckTransferRetrier.class);
    private static final Set<TransferStatus> IN_PROGRESS =
            Set.of(TransferStatus.PENDING, TransferStatus.DEBITED, TransferStatus.COMPENSATING);

    private final TransferRepository transfers;
    private final CommandPublisher publisher;
    private final Duration stuckAfter;

    public StuckTransferRetrier(TransferRepository transfers, CommandPublisher publisher,
                                @Value("${wallet.retry.stuck-after-seconds:60}") long stuckAfterSeconds) {
        this.transfers = transfers;
        this.publisher = publisher;
        this.stuckAfter = Duration.ofSeconds(stuckAfterSeconds);
    }

    @Scheduled(fixedDelayString = "${wallet.retry.interval-ms:30000}")
    public void resendStuckTransfers() {
        List<Transfer> stuck = transfers.findByStatusInAndUpdatedAtBefore(IN_PROGRESS, Instant.now().minus(stuckAfter));
        for (Transfer transfer : stuck) {
            log.warn("Transferencia {} parada em {} desde {}, reenviando comando",
                    transfer.getId(), transfer.getStatus(), transfer.getUpdatedAt());
            try {
                transfer.currentCommand().ifPresent(publisher::send);
            } catch (RuntimeException e) {
                log.error("Reenvio falhou para a transferencia {}, tenta de novo no proximo ciclo", transfer.getId(), e);
            }
        }
    }
}
