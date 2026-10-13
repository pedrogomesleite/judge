package com.packges.judge.messaging;

import java.util.UUID;

/**
 * Emitido quando uma solução é persistida como PENDENTE. É o ponto de extensão da publicação no broker (ADR-007):
 * hoje é consumido pelo {@link RunnerSimulado}; depois, por um publicador RabbitMQ após o commit.
 */
public record SolucaoEnviadaEvent(
        UUID solucaoId,
        UUID atividadeId,
        String fila,
        String codigo
) {
}
