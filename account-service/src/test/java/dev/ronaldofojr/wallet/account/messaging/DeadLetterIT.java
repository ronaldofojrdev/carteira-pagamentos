package dev.ronaldofojr.wallet.account.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ronaldofojr.wallet.account.IntegrationTestBase;
import dev.ronaldofojr.wallet.contracts.WalletMessaging;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Mensagem que nao da para processar nao pode ficar em loop: depois das tentativas ela vai para a DLQ. */
@AutoConfigureMockMvc
class DeadLetterIT extends IntegrationTestBase {

    @Autowired
    RabbitTemplate rabbitTemplate;
    @Autowired
    AmqpAdmin amqpAdmin;
    @Autowired
    MockMvc mvc;

    @BeforeEach
    void emptyTheQueues() {
        amqpAdmin.purgeQueue(WalletMessaging.ACCOUNT_COMMANDS_DLQ, true);
        amqpAdmin.purgeQueue(WalletMessaging.ACCOUNT_COMMANDS_QUEUE, true);
    }

    private void sendRaw(String body) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        Message message = MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8))
                .andProperties(properties).build();
        rabbitTemplate.send(WalletMessaging.COMMANDS_EXCHANGE, WalletMessaging.ACCOUNT_COMMAND_KEY, message);
    }

    private Message awaitDeadLetter() {
        Message[] holder = new Message[1];
        await().atMost(Duration.ofSeconds(30)).until(() -> {
            holder[0] = rabbitTemplate.receive(WalletMessaging.ACCOUNT_COMMANDS_DLQ);
            return holder[0] != null;
        });
        return holder[0];
    }

    @Test
    void messageThatIsNotValidJsonGoesToTheDeadLetterQueue() {
        sendRaw("isso nao e json");

        Message dead = awaitDeadLetter();

        assertThat(new String(dead.getBody(), StandardCharsets.UTF_8)).isEqualTo("isso nao e json");
        assertThat(rabbitTemplate.receive(WalletMessaging.ACCOUNT_COMMANDS_QUEUE)).as("saiu da fila principal").isNull();
    }

    @Test
    void validJsonThatBreaksTheProcessingIsRetriedAndThenGoesToTheDeadLetterQueue() {
        // sem accountId nem amount: passa na conversao e estoura dentro do processamento (NullPointerException)
        sendRaw("{\"transferId\":\"9b1c1f0e-5a53-4f6e-9f4e-0d0e3a1d2c10\",\"type\":\"DEBIT\"}");

        Message dead = awaitDeadLetter();

        assertThat(new String(dead.getBody(), StandardCharsets.UTF_8)).contains("9b1c1f0e-5a53-4f6e-9f4e-0d0e3a1d2c10");
    }

    @Test
    void theAdminEndpointCountsTheStuckMessages() throws Exception {
        mvc.perform(get("/admin/dead-letters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queue").value("account.commands.dlq"))
                .andExpect(jsonPath("$.messages").value(0));

        sendRaw("lixo 1");
        sendRaw("lixo 2");

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                mvc.perform(get("/admin/dead-letters")).andExpect(jsonPath("$.messages").value(2)));
    }

    @Test
    void aValidMessageAfterAPoisonOneIsStillProcessed() {
        sendRaw("lixo antes");
        awaitDeadLetter();

        // a fila nao ficou travada: a conta inexistente gera resposta de falha normalmente
        sendRaw("{\"transferId\":\"11111111-1111-1111-1111-111111111111\",\"type\":\"CREDIT\","
                + "\"accountId\":\"22222222-2222-2222-2222-222222222222\",\"amount\":5.00}");

        await().atMost(Duration.ofSeconds(15)).until(() ->
                rabbitTemplate.receive(WalletMessaging.TRANSFER_REPLIES_QUEUE) != null);
    }
}
