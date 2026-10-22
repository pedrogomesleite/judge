package com.packges.judge.config;

import com.packges.judge.dto.solucao.ExecucaoIniciada;
import com.packges.judge.dto.solucao.PedidoExecucao;
import com.packges.judge.dto.solucao.ResultadoExecucao;
import com.packges.judge.messaging.Mensageria;
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
 * Topologia e formato das mensagens trocadas com os runners (ADR-006, docs/architecture.md 3.3.1).
 */
@Configuration
public class RabbitConfig {

    /**
     * JSON com o tipo da mensagem no header __TypeId__. Usa ids neutros, e não o nome da classe Java, porque o runner
     * é outro projeto. O Spring Boot usa este conversor no RabbitTemplate e nos listeners.
     */
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
     * Parte fixa da topologia, declarada pelo RabbitAdmin a cada conexão. As filas por linguagem (Linguagem.fila) são
     * declaradas sob demanda pelo PublicadorSolucoes e pelo runner de cada linguagem.
     */
    @Bean
    public Declarables topologia() {
        DirectExchange submissoes = ExchangeBuilder.directExchange(Mensageria.EXCHANGE).durable(true).build();
        FanoutExchange dlx = ExchangeBuilder.fanoutExchange(Mensageria.DLX).durable(true).build();
        Queue resultados = Mensageria.filaDeTrabalho(Mensageria.FILA_RESULTADOS);
        // A DLQ não tem DLX: o que falhar no consumo dela é descartado (e registrado no log)
        Queue dlq = QueueBuilder.durable(Mensageria.FILA_DLQ).quorum().build();

        return new Declarables(
                submissoes, dlx, resultados, dlq,
                BindingBuilder.bind(resultados).to(submissoes).with(Mensageria.FILA_RESULTADOS),
                BindingBuilder.bind(dlq).to(dlx)
        );
    }
}
