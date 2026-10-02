package com.packges.judge.dto.atividade;

import com.packges.judge.enums.TipoCasoDeTeste;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * @param grupoId obrigatório para casos COMUNITARIO e ignorado para OFICIAL.
 */
public record CasoDeTesteRequest(
        @NotNull String entrada,
        @NotNull String saidaEsperada,
        @NotNull TipoCasoDeTeste tipo,
        UUID grupoId
) {
}
