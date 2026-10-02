package com.packges.judge.dto.grupo;

import com.packges.judge.entity.GrupoAtividade;
import com.packges.judge.enums.PapelGrupo;
import com.packges.judge.enums.StatusGrupo;

import java.time.Instant;
import java.util.UUID;

public record GrupoResponse(
        UUID id,
        String nome,
        String descricao,
        StatusGrupo status,
        Instant dataInicio,
        Instant dataFim,
        String codigoConvite,
        PapelGrupo meuPapel
) {
    public static GrupoResponse from(GrupoAtividade grupo, PapelGrupo meuPapel) {
        return new GrupoResponse(
                grupo.getId(),
                grupo.getNome(),
                grupo.getDescricao(),
                grupo.getStatus(),
                grupo.getDataInicio(),
                grupo.getDataFim(),
                grupo.getCodigoConvite(),
                meuPapel
        );
    }
}
