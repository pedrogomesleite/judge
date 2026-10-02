package com.packges.judge.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@ConfigurationProperties("juizin.jwt")
public record JwtProperties(
        String secret,
        Duration expiration
) {
    /** HS256 exige uma chave de pelo menos 256 bits. */
    private static final int TAMANHO_MINIMO_SEGREDO_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < TAMANHO_MINIMO_SEGREDO_BYTES) {
            throw new IllegalStateException("juizin.jwt.secret deve ter no mínimo 32 bytes");
        }
        if (expiration == null) {
            expiration = Duration.ofHours(2);
        }
    }
}
