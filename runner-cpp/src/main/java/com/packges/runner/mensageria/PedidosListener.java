package com.packges.runner.mensageria;

import com.packges.runner.avaliacao.AvaliadorCpp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consome a fila da linguagem (competing consumers, um consumidor por box).
 * <p>
 * O ack (modo AUTO) só sai quando o método retorna, ou seja, depois que o broker confirmou o resultado. Se o runner
 * cair no meio, a mensagem é reentregue (ADR-006). Uma exceção gera novas tentativas e, depois, a DLQ, onde a API
 * marca a solução como FALHA.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PedidosListener {

    private final AvaliadorCpp avaliador;
    private final PublicadorResultados publicador;

    @RabbitListener(queues = "${juizin.runner.fila}")
    public void executar(PedidoExecucao pedido) {
        log.info("Solução {} recebida (atividade {})", pedido.solucaoId(), pedido.atividadeId());
        publicador.publicar(new ExecucaoIniciada(pedido.solucaoId()));

        ResultadoExecucao resultado = avaliador.avaliar(pedido);

        publicador.publicar(resultado);
        log.info("Solução {} avaliada: {} ({} ms, {} KB)",
                pedido.solucaoId(), resultado.veredito(), resultado.tempoMs(), resultado.memoriaKb());
    }
}
