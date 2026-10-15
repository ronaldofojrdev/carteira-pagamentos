package dev.ronaldofojr.wallet.transaction.messaging;

import dev.ronaldofojr.wallet.contracts.AccountReply;
import dev.ronaldofojr.wallet.contracts.WalletMessaging;
import dev.ronaldofojr.wallet.transaction.service.TransferService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AccountReplyListener {

    private final TransferService service;

    public AccountReplyListener(TransferService service) {
        this.service = service;
    }

    @RabbitListener(queues = WalletMessaging.TRANSFER_REPLIES_QUEUE)
    public void onReply(AccountReply reply) {
        service.handleReply(reply);
    }
}
