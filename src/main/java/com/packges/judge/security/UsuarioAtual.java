package com.packges.judge.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Extrai o id do usuário autenticado a partir do JWT ("sub").
 */
public final class UsuarioAtual {

    private UsuarioAtual() {
    }

    public static UUID id(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
