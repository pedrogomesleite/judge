package com.packges.judge.messaging;

import com.packges.judge.dto.solucao.ExecucaoIniciada;
import com.packges.judge.dto.solucao.ResultadoExecucao;
import com.packges.judge.service.SolucaoService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Consome os avisos e resultados publicados pelos runners em submissions.results (ADR-006). O tipo da mensagem
 * (header __TypeId__) escolhe o handler. A entrega é at-least-once, então o SolucaoService é idempotente pelo
 * solucaoId. Mensagens inválidas, ou que continuam falhando depois das novas tentativas, vão para a DLQ.
 */
@Component
@RequiredArgsConstructor
@RabbitListener(queues = Mensageria.FILA_RESULTADOS)
public class ResultadosListener {

    private final SolucaoService solucaoService;
    private final Validator validator;

    @RabbitHandler
    public void aoIniciar(ExecucaoIniciada aviso) {
        validar(aviso);
        solucaoService.marcarEmExecucao(aviso.solucaoId());
    }

    @RabbitHandler
    public void aoConcluir(ResultadoExecucao resultado) {
        validar(resultado);
        solucaoService.registrarResultado(resultado);
    }

    private void validar(Object mensagem) {
        Set<ConstraintViolation<Object>> violacoes = validator.validate(mensagem);
        if (!violacoes.isEmpty()) {
            throw new AmqpRejectAndDontRequeueException("Mensagem inválida em %s: %s"
                    .formatted(Mensageria.FILA_RESULTADOS, violacoes));
        }
    }
}
