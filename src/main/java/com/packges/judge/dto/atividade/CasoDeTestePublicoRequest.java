package com.packges.judge.dto.atividade;

import jakarta.validation.constraints.NotNull;

/**
 * Marca ou desmarca um caso OFICIAL como público (exemplo visível para qualquer pessoa).
 */
public record CasoDeTestePublicoRequest(
        @NotNull Boolean publico
) {
}
