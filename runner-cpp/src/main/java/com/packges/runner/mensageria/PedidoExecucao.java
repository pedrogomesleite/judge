package com.packges.runner.mensageria;

import java.util.List;
import java.util.UUID;

/**
 * Pedido de execução consumido da fila da linguagem (PEDIDO_EXECUCAO). Espelha
 * {@code com.packges.judge.dto.solucao.PedidoExecucao}.
 */
public record PedidoExecucao(
        UUID solucaoId,
        UUID atividadeId,
        String linguagem,
        String codigo,
        int limiteTempoMs,
        int limiteMemoriaMb,
        List<UUID> casosDeTesteIds
) {
}
