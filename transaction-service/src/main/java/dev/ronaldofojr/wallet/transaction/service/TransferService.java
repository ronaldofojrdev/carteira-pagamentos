package dev.ronaldofojr.wallet.transaction.service;

import dev.ronaldofojr.wallet.contracts.AccountReply;
import dev.ronaldofojr.wallet.transaction.domain.IdempotencyConflictException;
import dev.ronaldofojr.wallet.transaction.domain.Transfer;
import dev.ronaldofojr.wallet.transaction.domain.TransferNotFoundException;
import dev.ronaldofojr.wallet.transaction.domain.TransferStatus;
import dev.ronaldofojr.wallet.transaction.messaging.CommandRequested;
import dev.ronaldofojr.wallet.transaction.metrics.TransferMetrics;
import dev.ronaldofojr.wallet.transaction.repository.TransferRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    public record CreationResult(Transfer transfer, boolean created) {
    }

    private final TransferRepository transfers;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final TransferMetrics metrics;

    public TransferService(TransferRepository transfers, ApplicationEventPublisher events, TransactionTemplate tx,
                           TransferMetrics metrics) {
        this.transfers = transfers;
        this.events = events;
        this.tx = tx;
        this.metrics = metrics;
    }

    /**
     * Cria a transferencia e dispara o passo 1 da saga (DEBIT).
     * Chamar de novo com a mesma Idempotency-Key e os mesmos dados devolve a mesma
     * transferencia sem criar outra. Dados diferentes com a mesma chave sao um erro.
     */
    public CreationResult create(String idempotencyKey, UUID from, UUID to, BigDecimal amount) {
        try {
            CreationResult result = tx.execute(status -> createInTransaction(idempotencyKey, from, to, amount));
            if (result.created()) {
                metrics.created();
            }
            return result;
        } catch (DataIntegrityViolationException e) {
            // duas requisicoes com a mesma chave ao mesmo tempo: a unique do banco barrou a segunda
            return tx.execute(status -> {
                Transfer existing = transfers.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> e);
                return new CreationResult(checkSameParameters(existing, from, to, amount), false);
            });
        }
    }

    private CreationResult createInTransaction(String key, UUID from, UUID to, BigDecimal amount) {
        Optional<Transfer> existing = transfers.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            return new CreationResult(checkSameParameters(existing.get(), from, to, amount), false);
        }
        Transfer transfer = transfers.saveAndFlush(new Transfer(key, from, to, amount));
        transfer.currentCommand().ifPresent(cmd -> events.publishEvent(new CommandRequested(cmd)));
        return new CreationResult(transfer, true);
    }

    private Transfer checkSameParameters(Transfer existing, UUID from, UUID to, BigDecimal amount) {
        if (!existing.hasSameParameters(from, to, amount)) {
            throw new IdempotencyConflictException(existing.getIdempotencyKey());
        }
        return existing;
    }

    @Transactional(readOnly = true)
    public Transfer get(UUID id) {
        return transfers.findById(id).orElseThrow(() -> new TransferNotFoundException(id));
    }

    /**
     * Avanca a saga conforme a resposta do account-service. Cada resposta so vale se a
     * transferencia estiver no estado esperado; respostas repetidas ou atrasadas sao
     * ignoradas, o que torna este lado idempotente tambem.
     */
    @Transactional
    public void handleReply(AccountReply reply) {
        Optional<Transfer> found = transfers.findById(reply.transferId());
        if (found.isEmpty()) {
            log.warn("Resposta para transferencia desconhecida {}, ignorando", reply.transferId());
            return;
        }
        Transfer transfer = found.get();

        switch (reply.type()) {
            case DEBIT -> {
                if (!expect(transfer, TransferStatus.PENDING, reply)) {
                    return;
                }
                if (reply.success()) {
                    transfer.markDebited();
                } else {
                    transfer.markFailed(reply.reason());
                }
            }
            case CREDIT -> {
                if (!expect(transfer, TransferStatus.DEBITED, reply)) {
                    return;
                }
                if (reply.success()) {
                    transfer.markCompleted();
                } else {
                    // passo 2 falhou depois de o dinheiro sair da origem: compensa
                    transfer.startCompensation(reply.reason());
                }
            }
            case REFUND -> {
                if (!expect(transfer, TransferStatus.COMPENSATING, reply)) {
                    return;
                }
                if (reply.success()) {
                    transfer.markRefunded();
                } else {
                    transfer.markCompensationFailed(reply.reason());
                    log.error("ESTORNO FALHOU na transferencia {}: {}", transfer.getId(), reply.reason());
                }
            }
        }

        transfers.save(transfer);
        if (transfer.getStatus().isFinal()) {
            TransferStatus finalStatus = transfer.getStatus();
            runAfterCommit(() -> metrics.finished(finalStatus));
        }
        // proximo passo da saga (vazio quando chegou a um estado final)
        transfer.currentCommand().ifPresent(cmd -> events.publishEvent(new CommandRequested(cmd)));
        log.info("Transferencia {} agora esta {}", transfer.getId(), transfer.getStatus());
    }

    // So conta depois do commit: se a transacao der rollback (ex: conflito de versao) e a mensagem for
    // reprocessada, a transferencia nao entra duas vezes na metrica.
    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private boolean expect(Transfer transfer, TransferStatus expected, AccountReply reply) {
        if (transfer.getStatus() == expected) {
            return true;
        }
        log.info("Resposta {} duplicada ou fora de ordem para a transferencia {} (estado {}), ignorando",
                reply.type(), transfer.getId(), transfer.getStatus());
        return false;
    }
}
