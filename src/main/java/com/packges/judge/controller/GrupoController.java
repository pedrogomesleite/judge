package com.packges.judge.controller;

import com.packges.judge.dto.grupo.DesempenhoEstudanteResponse;
import com.packges.judge.dto.grupo.GrupoAtividadeItemResponse;
import com.packges.judge.dto.grupo.GrupoAtividadePesoRequest;
import com.packges.judge.dto.grupo.GrupoAtividadeRequest;
import com.packges.judge.dto.grupo.GrupoRequest;
import com.packges.judge.dto.grupo.GrupoResponse;
import com.packges.judge.dto.grupo.MembroResponse;
import com.packges.judge.dto.grupo.ProgressoMembroResponse;
import com.packges.judge.security.UsuarioAtual;
import com.packges.judge.service.DesempenhoGrupoService;
import com.packges.judge.service.EventosGrupoService;
import com.packges.judge.service.GrupoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GrupoController {

    private final GrupoService grupoService;
    private final DesempenhoGrupoService desempenhoService;
    private final EventosGrupoService eventosService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GrupoResponse criar(@Valid @RequestBody GrupoRequest request,
                               @AuthenticationPrincipal Jwt jwt) {
        return grupoService.criar(request, UsuarioAtual.id(jwt));
    }

    @GetMapping
    public List<GrupoResponse> listarMeus(@AuthenticationPrincipal Jwt jwt) {
        return grupoService.listarMeus(UsuarioAtual.id(jwt));
    }

    @GetMapping("/{id}")
    public GrupoResponse buscar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return grupoService.buscar(id, UsuarioAtual.id(jwt));
    }

    @PutMapping("/{id}")
    public GrupoResponse editar(@PathVariable UUID id,
                                @Valid @RequestBody GrupoRequest request,
                                @AuthenticationPrincipal Jwt jwt) {
        return grupoService.editar(id, request, UsuarioAtual.id(jwt));
    }

    @GetMapping("/{id}/activities")
    public List<GrupoAtividadeItemResponse> listarAtividades(@PathVariable UUID id,
                                                             @AuthenticationPrincipal Jwt jwt) {
        return grupoService.listarAtividades(id, UsuarioAtual.id(jwt));
    }

    @PostMapping("/{id}/activities")
    @ResponseStatus(HttpStatus.CREATED)
    public GrupoAtividadeItemResponse adicionarAtividade(@PathVariable UUID id,
                                                         @Valid @RequestBody GrupoAtividadeRequest request,
                                                         @AuthenticationPrincipal Jwt jwt) {
        return grupoService.adicionarAtividade(id, request, UsuarioAtual.id(jwt));
    }

    @PatchMapping("/{id}/activities/{activityId}")
    public GrupoAtividadeItemResponse alterarPeso(@PathVariable UUID id,
                                                  @PathVariable UUID activityId,
                                                  @Valid @RequestBody GrupoAtividadePesoRequest request,
                                                  @AuthenticationPrincipal Jwt jwt) {
        return grupoService.alterarPeso(id, activityId, request.peso(), UsuarioAtual.id(jwt));
    }

    @DeleteMapping("/{id}/activities/{activityId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerAtividade(@PathVariable UUID id,
                                 @PathVariable UUID activityId,
                                 @AuthenticationPrincipal Jwt jwt) {
        grupoService.removerAtividade(id, activityId, UsuarioAtual.id(jwt));
    }

    @PostMapping("/{id}/start")
    public GrupoResponse iniciar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return grupoService.iniciar(id, UsuarioAtual.id(jwt));
    }

    @PostMapping("/{id}/finish")
    public GrupoResponse finalizar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return grupoService.finalizar(id, UsuarioAtual.id(jwt));
    }

    @GetMapping("/{id}/members")
    public List<MembroResponse> listarMembros(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return grupoService.listarMembros(id, UsuarioAtual.id(jwt));
    }

    @PatchMapping("/{id}/members/{userId}/promote")
    public MembroResponse promover(@PathVariable UUID id,
                                   @PathVariable UUID userId,
                                   @AuthenticationPrincipal Jwt jwt) {
        return grupoService.promover(id, userId, UsuarioAtual.id(jwt));
    }

    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerMembro(@PathVariable UUID id,
                              @PathVariable UUID userId,
                              @AuthenticationPrincipal Jwt jwt) {
        grupoService.removerMembro(id, userId, UsuarioAtual.id(jwt));
    }

    @GetMapping("/{id}/progress")
    public List<ProgressoMembroResponse> progresso(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return desempenhoService.progresso(id, UsuarioAtual.id(jwt));
    }

    @GetMapping("/{id}/report")
    public List<DesempenhoEstudanteResponse> relatorio(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return desempenhoService.relatorio(id, UsuarioAtual.id(jwt));
    }

    /**
     * Stream SSE com o tipo de cada alteração do grupo (ver EventosGrupoService). Para os erros (403/404) chegarem
     * em JSON, o cliente deve aceitar também application/json.
     */
    @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter eventos(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return eventosService.inscrever(id, UsuarioAtual.id(jwt));
    }

    @PostMapping("/join/{inviteCode}")
    public GrupoResponse entrarPorConvite(@PathVariable String inviteCode, @AuthenticationPrincipal Jwt jwt) {
        return grupoService.entrarPorConvite(inviteCode, UsuarioAtual.id(jwt));
    }
}
