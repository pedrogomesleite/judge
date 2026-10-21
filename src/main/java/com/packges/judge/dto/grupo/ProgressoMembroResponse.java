package com.packges.judge.dto.grupo;

import java.util.UUID;

/**
 * Placar de um estudante: atividades com pelo menos um envio aceito (sem contar repetidos) sobre o total do grupo.
 */
public record ProgressoMembroResponse(
        UUID usuarioId,
        String nome,
        int aceitas,
        int totalAtividades
) {
}
