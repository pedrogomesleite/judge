package com.packges.judge.dto.solucao;

import com.packges.judge.enums.Veredito;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Resultado de execução produzido pelo runner. É o contrato da mensagem EXECUCAO_CONCLUIDA da fila submissions.results.
 * O runner executa os casos até a primeira falha, então casos traz só os casos executados.
 */
public record ResultadoExecucao(
        @NotNull UUID solucaoId,
        @NotNull Veredito veredito,
        Integer tempoMs,
        Integer memoriaKb,
        @Valid List<ResultadoCaso> casos
) {
    public record ResultadoCaso(
            @NotNull UUID casoDeTesteId,
            @NotNull Veredito veredito,
            Integer tempoMs,
            Integer memoriaKb
    ) {
    }
}
