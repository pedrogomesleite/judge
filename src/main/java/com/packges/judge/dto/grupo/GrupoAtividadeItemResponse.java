package com.packges.judge.dto.grupo;

import com.packges.judge.entity.GrupoAtividadeItem;

import java.util.UUID;

public record GrupoAtividadeItemResponse(
        UUID atividadeId,
        String titulo,
        Integer ordem
) {
    public static GrupoAtividadeItemResponse from(GrupoAtividadeItem item) {
        return new GrupoAtividadeItemResponse(
                item.getAtividade().getId(),
                item.getAtividade().getTitulo(),
                item.getOrdem()
        );
    }
}
