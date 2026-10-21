package com.packges.judge.dto.grupo;

import java.util.UUID;

/**
 * Desempenho de um estudante em uma atividade do grupo. Um envio aceito vale o peso da atividade, uma única vez.
 */
public record DesempenhoAtividadeResponse(
        UUID atividadeId,
        int peso,
        long tentativas,
        boolean aceita,
        int pontos
) {
}
