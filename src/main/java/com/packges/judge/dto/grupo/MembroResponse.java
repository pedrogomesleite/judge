package com.packges.judge.dto.grupo;

import com.packges.judge.entity.MembroGrupo;
import com.packges.judge.enums.PapelGrupo;

import java.time.Instant;
import java.util.UUID;

public record MembroResponse(
        UUID usuarioId,
        String nome,
        PapelGrupo papel,
        Instant entrouEm
) {
    public static MembroResponse from(MembroGrupo membro) {
        return new MembroResponse(
                membro.getUsuario().getId(),
                membro.getUsuario().getNome(),
                membro.getPapel(),
                membro.getEntrouEm()
        );
    }
}
