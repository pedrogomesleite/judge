package com.packges.judge.dto.atividade;

import com.packges.judge.entity.CasoDeTeste;

import java.util.UUID;

/**
 * Caso de teste OFICIAL no formato de que o runner precisa para executar a solução (endpoint interno, ADR-008).
 */
public record CasoDeTesteExecucaoResponse(
        UUID id,
        String entrada,
        String saidaEsperada
) {
    public static CasoDeTesteExecucaoResponse from(CasoDeTeste caso) {
        return new CasoDeTesteExecucaoResponse(caso.getId(), caso.getEntrada(), caso.getSaidaEsperada());
    }
}
