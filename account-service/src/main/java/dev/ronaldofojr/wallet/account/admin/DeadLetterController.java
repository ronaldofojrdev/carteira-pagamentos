package dev.ronaldofojr.wallet.account.admin;

import dev.ronaldofojr.wallet.contracts.WalletMessaging;
import java.util.Properties;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mostra quantas mensagens deram erro e pararam na DLQ (dead letter queue).
 * Mensagem na DLQ e uma mensagem que falhou todas as tentativas: alguem precisa olhar.
 */
@RestController
@RequestMapping("/admin/dead-letters")
public class DeadLetterController {

    public record DeadLetterStatus(String queue, int messages) {
    }

    private final AmqpAdmin amqpAdmin;

    public DeadLetterController(AmqpAdmin amqpAdmin) {
        this.amqpAdmin = amqpAdmin;
    }

    @GetMapping
    public DeadLetterStatus status() {
        Properties properties = amqpAdmin.getQueueProperties(WalletMessaging.ACCOUNT_COMMANDS_DLQ);
        int messages = properties == null ? 0 : (Integer) properties.get(RabbitAdmin.QUEUE_MESSAGE_COUNT);
        return new DeadLetterStatus(WalletMessaging.ACCOUNT_COMMANDS_DLQ, messages);
    }
}
