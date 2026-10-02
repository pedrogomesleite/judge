package com.packges.judge.dto.usuario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UsuarioCreateRequest(
        @NotBlank @Size(max = 120) String nome,
        @NotBlank @Size(min = 3, max = 60) String login,
        @NotBlank @Size(min = 8, max = 72) String senha
) {
}
