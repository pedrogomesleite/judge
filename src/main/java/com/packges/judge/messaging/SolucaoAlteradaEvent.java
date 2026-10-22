package com.packges.judge.messaging;

import com.packges.judge.dto.solucao.SolucaoResponse;

/**
 * Emitido quando a solução muda de status. Depois do commit, o {@code EventosSolucaoService} envia o novo estado a
 * quem acompanha a solução pelo SSE (ADR-003). Os inscritos já passaram pelas mesmas regras de visibilidade do GET.
 */
public record SolucaoAlteradaEvent(
        SolucaoResponse solucao
) {
}
