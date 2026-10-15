package dev.ronaldofojr.wallet.account.service;

import dev.ronaldofojr.wallet.account.domain.Account;
import dev.ronaldofojr.wallet.account.domain.AccountNotFoundException;
import dev.ronaldofojr.wallet.account.domain.BusinessException;
import dev.ronaldofojr.wallet.account.domain.LedgerEntry;
import dev.ronaldofojr.wallet.account.domain.ProcessedCommand;
import dev.ronaldofojr.wallet.account.repository.AccountRepository;
import dev.ronaldofojr.wallet.account.repository.LedgerEntryRepository;
import dev.ronaldofojr.wallet.account.repository.ProcessedCommandRepository;
import dev.ronaldofojr.wallet.contracts.AccountCommand;
import dev.ronaldofojr.wallet.contracts.AccountReply;
import dev.ronaldofojr.wallet.contracts.CommandType;
import java.math.RoundingMode;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountCommandService {

    private static final Logger log = LoggerFactory.getLogger(AccountCommandService.class);

    private final AccountRepository accounts;
    private final LedgerEntryRepository ledger;
    private final ProcessedCommandRepository processed;

    public AccountCommandService(AccountRepository accounts, LedgerEntryRepository ledger,
                                 ProcessedCommandRepository processed) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.processed = processed;
    }

    /**
     * Aplica o comando uma unica vez. Se (transferId, type) ja foi tratado, devolve o
     * resultado guardado, inclusive quando foi uma falha de negocio. Sem isso, um
     * redelivery depois de o saldo mudar poderia transformar uma falha em sucesso.
     */
    @Transactional
    public AccountReply process(AccountCommand command) {
        Optional<ProcessedCommand> previous =
                processed.findByTransferIdAndCommandType(command.transferId(), command.type());
        if (previous.isPresent()) {
            log.info("Comando {} da transferencia {} ja processado, devolvendo resposta guardada",
                    command.type(), command.transferId());
            return previous.get().toReply();
        }

        ProcessedCommand result;
        try {
            apply(command);
            result = new ProcessedCommand(command.transferId(), command.type(), true, null);
        } catch (BusinessException e) {
            log.warn("Comando {} da transferencia {} recusado: {}", command.type(), command.transferId(), e.getMessage());
            result = new ProcessedCommand(command.transferId(), command.type(), false, e.getMessage());
        }
        // flush aqui para uma violacao da unique (processamento concorrente) estourar agora
        processed.saveAndFlush(result);
        return result.toReply();
    }

    private void apply(AccountCommand command) {
        Account account = accounts.findById(command.accountId())
                .orElseThrow(() -> new AccountNotFoundException(command.accountId()));

        if (command.type() == CommandType.DEBIT) {
            account.debit(command.amount());
        } else {
            // CREDIT e REFUND somam no saldo; o tipo so muda o que aparece no extrato
            account.credit(command.amount());
        }

        accounts.save(account);
        ledger.save(new LedgerEntry(account.getId(), command.transferId(), command.type(),
                command.amount().setScale(2, RoundingMode.HALF_EVEN), account.getBalance()));
    }
}
