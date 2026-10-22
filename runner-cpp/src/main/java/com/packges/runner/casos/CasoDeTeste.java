package com.packges.runner.casos;

import java.util.UUID;

/** Caso de teste OFICIAL lido do endpoint interno da API (CasoDeTesteExecucaoResponse). */
public record CasoDeTeste(
        UUID id,
        String entrada,
        String saidaEsperada
) {
}
