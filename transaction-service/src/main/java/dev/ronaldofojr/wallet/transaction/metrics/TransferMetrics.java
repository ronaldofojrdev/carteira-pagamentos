package dev.ronaldofojr.wallet.transaction.metrics;

import dev.ronaldofojr.wallet.transaction.domain.TransferStatus;
import dev.ronaldofojr.wallet.transaction.repository.TransferRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Metricas das transferencias, visiveis em /actuator/metrics:
 *   wallet.transfers.created                 quantas transferencias foram aceitas
 *   wallet.transfers.finished (tag status)   quantas terminaram, por estado final
 *   wallet.transfers.in.progress             quantas ainda estao no meio da saga
 */
@Component
public class TransferMetrics {

    private static final Set<TransferStatus> IN_PROGRESS =
            Set.of(TransferStatus.PENDING, TransferStatus.DEBITED, TransferStatus.COMPENSATING);

    private final MeterRegistry registry;

    public TransferMetrics(MeterRegistry registry, TransferRepository transfers) {
        this.registry = registry;
        Gauge.builder("wallet.transfers.in.progress", () -> transfers.countByStatusIn(IN_PROGRESS))
                .description("Transferencias em andamento (PENDING, DEBITED ou COMPENSATING)")
                .register(registry);
    }

    public void created() {
        registry.counter("wallet.transfers.created").increment();
    }

    public void finished(TransferStatus status) {
        registry.counter("wallet.transfers.finished", "status", status.name()).increment();
    }
}
