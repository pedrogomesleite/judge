package com.packges.judge.dto.grupo;

import java.util.List;
import java.util.UUID;

/**
 * Consolidação dos envios de um estudante no grupo, com uma linha por atividade (na ordem do grupo).
 */
public record DesempenhoEstudanteResponse(
        UUID usuarioId,
        String nome,
        int aceitas,
        int pontos,
        List<DesempenhoAtividadeResponse> atividades
) {
}
