package dev.ronaldofojr.wallet.transaction.messaging;

import dev.ronaldofojr.wallet.contracts.AccountCommand;
import dev.ronaldofojr.wallet.contracts.WalletMessaging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class CommandPublisher {

    private static final Logger log = LoggerFactory.getLogger(CommandPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public CommandPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    // So publica depois do commit: assim o account-service nunca recebe um comando de uma
    // transferencia que nao existe no banco. O risco inverso (commit ok, broker fora do ar)
    // e coberto pelo StuckTransferRetrier.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommandRequested(CommandRequested event) {
        try {
            send(event.command());
        } catch (RuntimeException e) {
            log.error("Falha ao publicar {} da transferencia {}. O retrier vai reenviar.",
                    event.command().type(), event.command().transferId(), e);
        }
    }

    public void send(AccountCommand command) {
        rabbitTemplate.convertAndSend(WalletMessaging.COMMANDS_EXCHANGE, WalletMessaging.ACCOUNT_COMMAND_KEY, command);
        log.info("Comando enviado: {} transferencia={}", command.type(), command.transferId());
    }
}
