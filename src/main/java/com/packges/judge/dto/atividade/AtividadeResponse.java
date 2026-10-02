package com.packges.judge.dto.atividade;

import com.packges.judge.entity.Atividade;

import java.time.Instant;
import java.util.UUID;

public record AtividadeResponse(
        UUID id,
        UUID autorId,
        String titulo,
        String enunciado,
        Integer limiteTempoMs,
        Integer limiteMemoriaMb,
        Instant criadoEm
) {
    public static AtividadeResponse from(Atividade atividade) {
        return new AtividadeResponse(
                atividade.getId(),
                atividade.getAutor().getId(),
                atividade.getTitulo(),
                atividade.getEnunciado(),
                atividade.getLimiteTempoMs(),
                atividade.getLimiteMemoriaMb(),
                atividade.getCriadoEm()
        );
    }
}
