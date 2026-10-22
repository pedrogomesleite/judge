package com.packges.judge.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Autentica o runner pelo token de serviço ({@code Authorization: Bearer <juizin.runner.token>}) nos endpoints
 * internos (ADR-008).
 * <p>
 * Não é um {@code @Component}: fica registrado só na chain de /api/internal/** (SecurityConfig), e não como um filtro
 * global da aplicação.
 */
public class RunnerTokenFilter extends OncePerRequestFilter {

    static final String PAPEL = "RUNNER";
    private static final String PREFIXO = "Bearer ";

    private final byte[] token;

    public RunnerTokenFilter(String token) {
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (autorizado(request.getHeader(HttpHeaders.AUTHORIZATION))) {
            SecurityContext contexto = SecurityContextHolder.createEmptyContext();
            contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    "runner", null, AuthorityUtils.createAuthorityList("ROLE_" + PAPEL)));
            SecurityContextHolder.setContext(contexto);
        }
        chain.doFilter(request, response);
    }

    /** Comparação em tempo constante, para não vazar o token pelo tempo de resposta. */
    private boolean autorizado(String cabecalho) {
        return cabecalho != null && cabecalho.startsWith(PREFIXO)
                && MessageDigest.isEqual(token, cabecalho.substring(PREFIXO.length()).getBytes(StandardCharsets.UTF_8));
    }
}
