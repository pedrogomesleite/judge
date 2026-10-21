package com.packges.judge.service;

import com.packges.judge.messaging.GrupoAlteradoEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE por grupo (ADR-003): avisa os membros conectados de que algo mudou no grupo. O evento leva só o tipo da
 * alteração ({@link com.packges.judge.enums.TipoAlteracaoGrupo}); o cliente busca de novo pelos endpoints,
 * que aplicam as permissões de cada papel.
 * <p>
 * As inscrições ficam em memória: com várias réplicas da API, os eventos precisarão ser distribuídos entre elas
 * (ver ADR-003).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventosGrupoService {

    private static final Duration TIMEOUT = Duration.ofMinutes(30);
    private static final String EVENTO_CONECTADO = "CONECTADO";

    private final GrupoService grupoService;

    private final Map<UUID, Set<Inscricao>> inscricoes = new ConcurrentHashMap<>();

    private record Inscricao(UUID usuarioId, SseEmitter emitter) {
    }

    public SseEmitter inscrever(UUID grupoId, UUID usuarioId) {
        grupoService.exigirMembro(grupoId, usuarioId);

        SseEmitter emitter = new SseEmitter(TIMEOUT.toMillis());
        Inscricao inscricao = new Inscricao(usuarioId, emitter);
        // compute é atômico por grupo: não concorre com a remoção do conjunto vazio em remover()
        inscricoes.compute(grupoId, (id, doGrupo) -> {
            Set<Inscricao> conjunto = doGrupo != null ? doGrupo : ConcurrentHashMap.newKeySet();
            conjunto.add(inscricao);
            return conjunto;
        });

        emitter.onCompletion(() -> remover(grupoId, inscricao));
        emitter.onTimeout(() -> remover(grupoId, inscricao));
        emitter.onError(erro -> remover(grupoId, inscricao));

        // Primeiro evento: envia os headers da resposta e confirma a conexão ao cliente
        enviar(grupoId, inscricao, SseEmitter.event().name(EVENTO_CONECTADO).data(EVENTO_CONECTADO));
        log.debug("Usuário {} conectado aos eventos do grupo {}", usuarioId, grupoId);
        return emitter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void aoAlterar(GrupoAlteradoEvent evento) {
        Set<Inscricao> doGrupo = inscricoes.get(evento.grupoId());
        if (doGrupo == null) {
            return;
        }
        String tipo = evento.tipo().name();
        for (Inscricao inscricao : doGrupo) {
            enviar(evento.grupoId(), inscricao, SseEmitter.event().name(tipo).data(tipo));
        }
    }

    /** Comentário periódico: mantém a conexão aberta em proxies que encerram conexões ociosas. */
    @Scheduled(fixedRate = 25_000)
    public void manterConexoes() {
        inscricoes.forEach((grupoId, doGrupo) ->
                doGrupo.forEach(inscricao -> enviar(grupoId, inscricao, SseEmitter.event().comment("ping"))));
    }

    private void enviar(UUID grupoId, Inscricao inscricao, SseEmitter.SseEventBuilder evento) {
        try {
            inscricao.emitter().send(evento);
        } catch (IOException | IllegalStateException e) {
            // Cliente desconectado ou emitter já encerrado
            remover(grupoId, inscricao);
        }
    }

    private void remover(UUID grupoId, Inscricao inscricao) {
        inscricoes.computeIfPresent(grupoId, (id, doGrupo) -> {
            doGrupo.remove(inscricao);
            return doGrupo.isEmpty() ? null : doGrupo;
        });
    }
}
