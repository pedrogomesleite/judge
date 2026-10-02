package com.packges.judge.controller;

import com.packges.judge.dto.atividade.AtividadeRequest;
import com.packges.judge.dto.atividade.AtividadeResponse;
import com.packges.judge.dto.atividade.CasoDeTesteRequest;
import com.packges.judge.dto.atividade.CasoDeTesteResponse;
import com.packges.judge.security.UsuarioAtual;
import com.packges.judge.service.AtividadeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
public class AtividadeController {

    private final AtividadeService atividadeService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AtividadeResponse criar(@Valid @RequestBody AtividadeRequest request,
                                   @AuthenticationPrincipal Jwt jwt) {
        return atividadeService.criar(request, UsuarioAtual.id(jwt));
    }

    @GetMapping
    public List<AtividadeResponse> listar() {
        return atividadeService.listar();
    }

    @GetMapping("/{id}")
    public AtividadeResponse buscar(@PathVariable UUID id) {
        return atividadeService.buscar(id);
    }

    @PutMapping("/{id}")
    public AtividadeResponse editar(@PathVariable UUID id,
                                    @Valid @RequestBody AtividadeRequest request,
                                    @AuthenticationPrincipal Jwt jwt) {
        return atividadeService.editar(id, request, UsuarioAtual.id(jwt));
    }

    @PostMapping("/{id}/test-cases")
    @ResponseStatus(HttpStatus.CREATED)
    public CasoDeTesteResponse criarCasoDeTeste(@PathVariable UUID id,
                                                @Valid @RequestBody CasoDeTesteRequest request,
                                                @AuthenticationPrincipal Jwt jwt) {
        return atividadeService.criarCasoDeTeste(id, request, UsuarioAtual.id(jwt));
    }

    @GetMapping("/{id}/test-cases")
    public List<CasoDeTesteResponse> listarCasosDeTeste(@PathVariable UUID id,
                                                        @RequestParam(required = false) UUID groupId,
                                                        @AuthenticationPrincipal Jwt jwt) {
        return atividadeService.listarCasosDeTeste(id, groupId, UsuarioAtual.id(jwt));
    }
}
