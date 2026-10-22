package com.packges.runner.mensageria;

import java.util.List;
import java.util.UUID;

/**
 * Resultado da execução (EXECUCAO_CONCLUIDA). Espelha {@code com.packges.judge.dto.solucao.ResultadoExecucao}.
 *
 * @param tempoMs   maior tempo de CPU entre os casos executados.
 * @param memoriaKb maior pico de memória entre os casos executados.
 * @param casos     casos executados, em ordem, até o primeiro que falhou.
 */
public record ResultadoExecucao(
        UUID solucaoId,
        Veredito veredito,
        Integer tempoMs,
        Integer memoriaKb,
        List<ResultadoCaso> casos
) {
    public record ResultadoCaso(
            UUID casoDeTesteId,
            Veredito veredito,
            Integer tempoMs,
            Integer memoriaKb
    ) {
    }
}
