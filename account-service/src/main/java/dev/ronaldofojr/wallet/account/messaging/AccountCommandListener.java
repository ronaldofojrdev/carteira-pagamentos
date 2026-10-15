package dev.ronaldofojr.wallet.account.messaging;

import dev.ronaldofojr.wallet.account.service.AccountCommandService;
import dev.ronaldofojr.wallet.contracts.AccountCommand;
import dev.ronaldofojr.wallet.contracts.AccountReply;
import dev.ronaldofojr.wallet.contracts.WalletMessaging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class AccountCommandListener {

    private static final Logger log = LoggerFactory.getLogger(AccountCommandListener.class);

    private final AccountCommandService service;
    private final RabbitTemplate rabbitTemplate;

    public AccountCommandListener(AccountCommandService service, RabbitTemplate rabbitTemplate) {
        this.service = service;
        this.rabbitTemplate = rabbitTemplate;
    }

    // Este metodo nao e transacional: a transacao do banco fecha dentro de service.process
    // e so depois respondemos. Se a resposta falhar, a mensagem e reentregue e o service
    // devolve a resposta guardada.
    @RabbitListener(queues = WalletMessaging.ACCOUNT_COMMANDS_QUEUE)
    public void onCommand(AccountCommand command) {
        log.info("Comando recebido: {} transferencia={}", command.type(), command.transferId());
        AccountReply reply = service.process(command);
        rabbitTemplate.convertAndSend(WalletMessaging.REPLIES_EXCHANGE, WalletMessaging.ACCOUNT_REPLY_KEY, reply);
    }
}
