package com.packges.runner.config;

import com.packges.runner.mensageria.ExecucaoIniciada;
import com.packges.runner.mensageria.Mensageria;
import com.packges.runner.mensageria.PedidoExecucao;
import com.packges.runner.mensageria.ResultadoExecucao;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.DefaultJacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Topologia e formato das mensagens, iguais aos da API (com.packges.judge.config.RabbitConfig).
 */
@Configuration
public class RabbitConfig {

    /** JSON com o tipo da mensagem no header __TypeId__, pelos ids neutros do contrato. */
    @Bean
    public JacksonJsonMessageConverter messageConverter() {
        DefaultJacksonJavaTypeMapper tipos = new DefaultJacksonJavaTypeMapper();
        tipos.setIdClassMapping(Map.of(
                Mensageria.TIPO_PEDIDO_EXECUCAO, PedidoExecucao.class,
                Mensageria.TIPO_EXECUCAO_INICIADA, ExecucaoIniciada.class,
                Mensageria.TIPO_EXECUCAO_CONCLUIDA, ResultadoExecucao.class
        ));
        JacksonJsonMessageConverter conversor = new JacksonJsonMessageConverter();
        conversor.setJavaTypeMapper(tipos);
        return conversor;
    }

    /**
     * Declara tudo de que o runner depende, com os mesmos argumentos da API: assim, tanto faz quem sobe primeiro.
     */
    @Bean
    public Declarables topologia(RunnerProperties properties) {
        DirectExchange submissoes = ExchangeBuilder.directExchange(Mensageria.EXCHANGE).durable(true).build();
        FanoutExchange dlx = ExchangeBuilder.fanoutExchange(Mensageria.DLX).durable(true).build();
        Queue pedidos = Mensageria.filaDeTrabalho(properties.fila());
        Queue resultados = Mensageria.filaDeTrabalho(Mensageria.FILA_RESULTADOS);
        Queue dlq = QueueBuilder.durable(Mensageria.FILA_DLQ).quorum().build();

        return new Declarables(
                submissoes, dlx, pedidos, resultados, dlq,
                BindingBuilder.bind(pedidos).to(submissoes).with(properties.fila()),
                BindingBuilder.bind(resultados).to(submissoes).with(Mensageria.FILA_RESULTADOS),
                BindingBuilder.bind(dlq).to(dlx)
        );
    }
}
