package com.packges.runner.mensageria;

import java.util.UUID;

/**
 * Aviso de que a execução começou (EXECUCAO_INICIADA); a API passa a solução para EM_EXECUCAO. Espelha
 * {@code com.packges.judge.dto.solucao.ExecucaoIniciada}.
 */
public record ExecucaoIniciada(
        UUID solucaoId
) {
}
