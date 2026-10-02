package com.packges.judge.dto.linguagem;

import com.packges.judge.entity.Linguagem;

import java.util.UUID;

public record LinguagemResponse(
        UUID id,
        String nome,
        String versao
) {
    public static LinguagemResponse from(Linguagem linguagem) {
        return new LinguagemResponse(linguagem.getId(), linguagem.getNome(), linguagem.getVersao());
    }
}
