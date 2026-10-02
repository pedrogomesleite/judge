package com.packges.judge.dto.atividade;

import com.packges.judge.entity.CasoDeTeste;
import com.packges.judge.enums.TipoCasoDeTeste;

import java.util.UUID;

public record CasoDeTesteResponse(
        UUID id,
        UUID atividadeId,
        UUID autorId,
        UUID grupoId,
        TipoCasoDeTeste tipo,
        String entrada,
        String saidaEsperada
) {
    public static CasoDeTesteResponse from(CasoDeTeste caso) {
        return new CasoDeTesteResponse(
                caso.getId(),
                caso.getAtividade().getId(),
                caso.getAutor().getId(),
                caso.getGrupo() != null ? caso.getGrupo().getId() : null,
                caso.getTipo(),
                caso.getEntrada(),
                caso.getSaidaEsperada()
        );
    }
}
