package com.packges.judge.dto.atividade;

import com.packges.judge.enums.TipoCasoDeTeste;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * @param grupoId obrigatório para casos COMUNITARIO e ignorado para OFICIAL.
 * @param publico só para OFICIAL (padrão false): o caso aparece como exemplo para qualquer pessoa.
 */
public record CasoDeTesteRequest(
        @NotNull String entrada,
        @NotNull String saidaEsperada,
        @NotNull TipoCasoDeTeste tipo,
        UUID grupoId,
        Boolean publico
) {
}
