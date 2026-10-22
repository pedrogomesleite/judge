package com.packges.judge.dto.solucao;

import java.util.List;
import java.util.UUID;

/**
 * Pedido de execução publicado na fila da linguagem (submissions.&lt;lang&gt;). É o contrato da mensagem
 * PEDIDO_EXECUCAO (docs/architecture.md, 3.3.1). Leva só os ids dos casos de teste OFICIAL: o runner busca os casos
 * no endpoint interno e os guarda em cache (ADR-008).
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
