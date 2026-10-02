package com.packges.judge.controller;

import com.packges.judge.dto.solucao.SolucaoRequest;
import com.packges.judge.dto.solucao.SolucaoResponse;
import com.packges.judge.security.UsuarioAtual;
import com.packges.judge.service.SolucaoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SolucaoController {

    private final SolucaoService solucaoService;

    /**
     * Retorna 202: a avaliação é assíncrona e o veredito chega depois (ver docs/architecture.md, 3.3).
     */
    @PostMapping
    public ResponseEntity<SolucaoResponse> enviar(@Valid @RequestBody SolucaoRequest request,
                                                  @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.accepted().body(solucaoService.enviar(request, UsuarioAtual.id(jwt)));
    }

    @GetMapping("/{id}")
    public SolucaoResponse buscar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return solucaoService.buscar(id, UsuarioAtual.id(jwt));
    }

    @GetMapping
    public List<SolucaoResponse> listar(@RequestParam(required = false) UUID groupId,
                                        @AuthenticationPrincipal Jwt jwt) {
        return solucaoService.listar(groupId, UsuarioAtual.id(jwt));
    }
}
