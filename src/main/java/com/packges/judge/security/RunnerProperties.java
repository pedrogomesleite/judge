package com.packges.judge.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;

/**
 * @param token token de serviço com que o runner acessa os endpoints internos (/api/internal/**, ADR-008).
 */
@ConfigurationProperties("juizin.runner")
public record RunnerProperties(
        String token
) {
    private static final int TAMANHO_MINIMO_TOKEN_BYTES = 32;

    public RunnerProperties {
        if (token == null || token.getBytes(StandardCharsets.UTF_8).length < TAMANHO_MINIMO_TOKEN_BYTES) {
            throw new IllegalStateException("juizin.runner.token deve ter no mínimo 32 bytes");
        }
    }
}
