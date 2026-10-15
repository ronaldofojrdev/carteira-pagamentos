package dev.ronaldofojr.wallet.transaction;

import dev.ronaldofojr.wallet.contracts.AccountCommand;
import dev.ronaldofojr.wallet.contracts.WalletMessaging;
import dev.ronaldofojr.wallet.transaction.repository.TransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

/**
 * Postgres e RabbitMQ reais. O account-service NAO sobe aqui: os testes leem os comandos
 * que o orquestrador publica na fila e injetam as respostas na mao, simulando o outro servico.
 */
@SpringBootTest(properties = "wallet.retry.enabled=false")
public abstract class IntegrationTestBase {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-alpine");

    static {
        POSTGRES.start();
        RABBIT.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
    }

    @Autowired
    protected RabbitTemplate rabbitTemplate;
    @Autowired
    protected AmqpAdmin amqpAdmin;
    @Autowired
    protected TransferRepository transfers;

    @BeforeEach
    void cleanState() {
        transfers.deleteAll();
        amqpAdmin.purgeQueue(WalletMessaging.ACCOUNT_COMMANDS_QUEUE, true);
    }

    /** Proximo comando publicado para o account-service (null se nada chegar em 3s). */
    protected AccountCommand nextCommand() {
        return rabbitTemplate.receiveAndConvert(WalletMessaging.ACCOUNT_COMMANDS_QUEUE, 3_000,
                new ParameterizedTypeReference<AccountCommand>() {
                });
    }
}
