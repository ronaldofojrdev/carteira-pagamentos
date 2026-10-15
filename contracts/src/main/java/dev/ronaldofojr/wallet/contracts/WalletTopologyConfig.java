package dev.ronaldofojr.wallet.contracts;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declara toda a topologia nos dois servicos. Declarar de novo com os mesmos
 * argumentos e inofensivo, e assim a ordem de subida dos servicos nao importa.
 *
 * Mensagens que estouram as tentativas de retry vao para a fila .dlq
 * (dead letter pela exchange default, routing key = nome da DLQ).
 */
@Configuration
public class WalletTopologyConfig {

    @Bean
    DirectExchange commandsExchange() {
        return new DirectExchange(WalletMessaging.COMMANDS_EXCHANGE);
    }

    @Bean
    DirectExchange repliesExchange() {
        return new DirectExchange(WalletMessaging.REPLIES_EXCHANGE);
    }

    @Bean
    Queue accountCommandsQueue() {
        return QueueBuilder.durable(WalletMessaging.ACCOUNT_COMMANDS_QUEUE)
                .deadLetterExchange("")
                .deadLetterRoutingKey(WalletMessaging.ACCOUNT_COMMANDS_DLQ)
                .build();
    }

    @Bean
    Queue accountCommandsDlq() {
        return QueueBuilder.durable(WalletMessaging.ACCOUNT_COMMANDS_DLQ).build();
    }

    @Bean
    Queue transferRepliesQueue() {
        return QueueBuilder.durable(WalletMessaging.TRANSFER_REPLIES_QUEUE)
                .deadLetterExchange("")
                .deadLetterRoutingKey(WalletMessaging.TRANSFER_REPLIES_DLQ)
                .build();
    }

    @Bean
    Queue transferRepliesDlq() {
        return QueueBuilder.durable(WalletMessaging.TRANSFER_REPLIES_DLQ).build();
    }

    @Bean
    Binding accountCommandsBinding() {
        return BindingBuilder.bind(accountCommandsQueue())
                .to(commandsExchange())
                .with(WalletMessaging.ACCOUNT_COMMAND_KEY);
    }

    @Bean
    Binding transferRepliesBinding() {
        return BindingBuilder.bind(transferRepliesQueue())
                .to(repliesExchange())
                .with(WalletMessaging.ACCOUNT_REPLY_KEY);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages(WalletMessaging.class.getPackageName());
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
