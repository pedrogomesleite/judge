package com.packges.judge.dto.atividade;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AtividadeRequest(
        @NotBlank @Size(max = 200) String titulo,
        @NotBlank String enunciado,
        @NotNull @Min(100) @Max(30_000) Integer limiteTempoMs,
        @NotNull @Min(16) @Max(1024) Integer limiteMemoriaMb
) {
}
