package com.packges.judge.messaging;

import com.packges.judge.dto.solucao.ExecucaoIniciada;
import com.packges.judge.dto.solucao.PedidoExecucao;
import com.packges.judge.dto.solucao.ResultadoExecucao;
import com.packges.judge.service.SolucaoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consome a DLQ (submissions.dlq), que recebe as mensagens rejeitadas ou que passaram do limite de entregas (ADR-006).
 * Um pedido que o runner não conseguiu executar, ou um resultado que não pôde ser gravado, marca a solução como FALHA
 * (ERRO_INTERNO). O que falhar aqui é descartado, porque a DLQ não tem DLX.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RabbitListener(queues = Mensageria.FILA_DLQ)
public class FalhasListener {

    private final SolucaoService solucaoService;

    @RabbitHandler
    public void aoFalharPedido(PedidoExecucao pedido) {
        log.error("O pedido de execução da solução {} foi para a DLQ", pedido.solucaoId());
        solucaoService.registrarFalha(pedido.solucaoId());
    }

    @RabbitHandler
    public void aoFalharResultado(ResultadoExecucao resultado) {
        log.error("O resultado da solução {} foi para a DLQ", resultado.solucaoId());
        solucaoService.registrarFalha(resultado.solucaoId());
    }

    /** Não marca FALHA: o resultado ainda pode chegar, e a solução finalizada o descartaria. */
    @RabbitHandler
    public void aoFalharAviso(ExecucaoIniciada aviso) {
        log.warn("O aviso de início da solução {} foi para a DLQ e foi descartado", aviso.solucaoId());
    }
}
