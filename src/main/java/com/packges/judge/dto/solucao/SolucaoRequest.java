package com.packges.judge.dto.solucao;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * @param grupoId opcional: nulo para soluções avulsas (fora de grupo).
 */
public record SolucaoRequest(
        @NotNull UUID atividadeId,
        @NotNull UUID linguagemId,
        UUID grupoId,
        @NotBlank @Size(max = 65_536) String codigo
) {
}
