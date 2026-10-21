package com.packges.judge.dto.grupo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * @param peso opcional: pontos que um envio aceito vale na atividade; sem peso, vale 1.
 */
public record GrupoAtividadeRequest(
        @NotNull UUID atividadeId,
        @NotNull @Min(0) Integer ordem,
        @Min(1) @Max(100) Integer peso
) {
}
