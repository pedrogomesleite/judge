package com.packges.judge.dto.grupo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record GrupoAtividadeRequest(
        @NotNull UUID atividadeId,
        @NotNull @Min(0) Integer ordem
) {
}
