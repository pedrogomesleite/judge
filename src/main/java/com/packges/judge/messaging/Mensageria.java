package com.packges.judge.messaging;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;

/**
 * Contrato da mensageria com os runners (ADR-006, docs/architecture.md 3.3.1).
 * <p>
 * Espelhado no runner (runner-cpp, {@code com.packges.runner.mensageria.Mensageria}): as duas pontas declaram as filas,
 * e argumentos diferentes na declaração fazem o RabbitMQ recusá-la (PRECONDITION_FAILED).
 */
public final class Mensageria {

    /** Exchange direct: cada fila é ligada com binding key igual ao próprio nome. */
    public static final String EXCHANGE = "juizin.submissions";
    public static final String FILA_RESULTADOS = "submissions.results";
    public static final String DLX = "juizin.dlx";
    public static final String FILA_DLQ = "submissions.dlq";

    /** Entregas de uma mesma mensagem (ex.: o runner caiu no meio da execução) antes de ela ir para a DLQ. */
    public static final int LIMITE_ENTREGAS = 3;

    /** Valores do header __TypeId__, que identifica o formato do corpo (JSON) da mensagem. */
    public static final String TIPO_PEDIDO_EXECUCAO = "PEDIDO_EXECUCAO";
    public static final String TIPO_EXECUCAO_INICIADA = "EXECUCAO_INICIADA";
    public static final String TIPO_EXECUCAO_CONCLUIDA = "EXECUCAO_CONCLUIDA";

    private Mensageria() {
    }

    /** Fila quorum de trabalho (submissions.&lt;lang&gt; e submissions.results), com DLX e limite de entregas. */
    public static Queue filaDeTrabalho(String nome) {
        return QueueBuilder.durable(nome)
                .quorum()
                .deadLetterExchange(DLX)
                .deliveryLimit(LIMITE_ENTREGAS)
                .build();
    }
}
