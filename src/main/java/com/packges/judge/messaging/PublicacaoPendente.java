package com.packges.judge.messaging;

import com.packges.judge.dto.solucao.PedidoExecucao;

/**
 * Solução pronta para ser publicada: a fila da linguagem (também usada como routing key) e o pedido de execução.
 */
public record PublicacaoPendente(
        String fila,
        PedidoExecucao pedido
) {
}
