package com.packges.judge.dto.solucao;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Aviso do runner de que começou a executar a solução (PENDENTE → EM_EXECUCAO). É o contrato da mensagem
 * EXECUCAO_INICIADA da fila submissions.results.
 */
public record ExecucaoIniciada(
        @NotNull UUID solucaoId
) {
}
