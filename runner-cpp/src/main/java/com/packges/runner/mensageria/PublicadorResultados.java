package com.packges.runner.mensageria;

import com.packges.runner.config.RunnerProperties;
import com.packges.runner.exception.FalhaInfraestruturaException;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publica os avisos e resultados em submissions.results e espera o publisher confirm. Sem o confirm, lança exceção:
 * o pedido não recebe ack e é tentado de novo (ADR-006).
 */
@Component
@RequiredArgsConstructor
public class PublicadorResultados {

    private final RabbitTemplate rabbitTemplate;
    private final RunnerProperties properties;

    public void publicar(ExecucaoIniciada aviso) {
        enviar(aviso.solucaoId(), aviso);
    }

    public void publicar(ResultadoExecucao resultado) {
        enviar(resultado.solucaoId(), resultado);
    }

    private void enviar(UUID solucaoId, Object mensagem) {
        CorrelationData correlacao = new CorrelationData();
        rabbitTemplate.convertAndSend(Mensageria.EXCHANGE, Mensageria.FILA_RESULTADOS, mensagem, correlacao);

        CorrelationData.Confirm confirmacao;
        try {
            confirmacao = correlacao.getFuture()
                    .get(properties.timeoutConfirmacao().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FalhaInfraestruturaException("Publicação interrompida (solução %s)".formatted(solucaoId), e);
        } catch (ExecutionException | TimeoutException e) {
            throw new FalhaInfraestruturaException("Sem confirmação do broker (solução %s)".formatted(solucaoId), e);
        }

        if (!confirmacao.ack()) {
            throw new FalhaInfraestruturaException("O broker recusou a mensagem da solução %s: %s"
                    .formatted(solucaoId, confirmacao.reason()));
        }
        if (correlacao.getReturned() != null) {
            throw new FalhaInfraestruturaException("Mensagem da solução %s sem fila de destino (%s)"
                    .formatted(solucaoId, Mensageria.FILA_RESULTADOS));
        }
    }
}
