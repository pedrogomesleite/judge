package com.packges.judge.dto.erro;

import java.time.Instant;
import java.util.Map;

/**
 * @param campos erros de validação por campo; vazio quando não se aplica.
 */
public record ErroResponse(
        int status,
        String mensagem,
        Instant timestamp,
        Map<String, String> campos
) {
    public static ErroResponse of(int status, String mensagem) {
        return new ErroResponse(status, mensagem, Instant.now(), Map.of());
    }
}
