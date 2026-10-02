package com.packges.judge.dto.grupo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * @param dataInicio opcional: quando informada, o grupo inicia automaticamente nessa data.
 * @param dataFim    opcional: quando informada, o grupo finaliza automaticamente nessa data.
 */
public record GrupoRequest(
        @NotBlank @Size(max = 120) String nome,
        @Size(max = 2000) String descricao,
        Instant dataInicio,
        Instant dataFim
) {
}
