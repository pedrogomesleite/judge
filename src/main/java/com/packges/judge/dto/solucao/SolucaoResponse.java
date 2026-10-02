package com.packges.judge.dto.solucao;

import com.packges.judge.entity.Solucao;
import com.packges.judge.enums.StatusSolucao;
import com.packges.judge.enums.Veredito;

import java.time.Instant;
import java.util.UUID;

public record SolucaoResponse(
        UUID id,
        UUID autorId,
        UUID atividadeId,
        UUID grupoId,
        String linguagem,
        StatusSolucao status,
        Veredito veredito,
        Integer tempoMs,
        Integer memoriaKb,
        Instant enviadoEm
) {
    public static SolucaoResponse from(Solucao solucao) {
        return new SolucaoResponse(
                solucao.getId(),
                solucao.getAutor().getId(),
                solucao.getAtividade().getId(),
                solucao.getGrupo() != null ? solucao.getGrupo().getId() : null,
                solucao.getLinguagem().getNome(),
                solucao.getStatus(),
                solucao.getVeredito(),
                solucao.getTempoMs(),
                solucao.getMemoriaKb(),
                solucao.getEnviadoEm()
        );
    }
}
