package com.packges.judge.messaging;

import java.util.UUID;

/**
 * Emitido quando uma solução é persistida como PENDENTE. Depois do commit, o {@link PublicadorSolucoes} a publica
 * na fila da linguagem (ADR-007).
 */
public record SolucaoEnviadaEvent(
        UUID solucaoId
) {
}
