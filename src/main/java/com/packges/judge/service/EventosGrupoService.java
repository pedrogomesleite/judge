package com.packges.judge.service;

import com.packges.judge.messaging.GrupoAlteradoEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.UUID;

/**
 * SSE por grupo (ADR-003): avisa os membros conectados de que algo mudou no grupo. O evento leva só o tipo da
 * alteração ({@link com.packges.judge.enums.TipoAlteracaoGrupo}); o cliente busca de novo pelos endpoints,
 * que aplicam as permissões de cada papel.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventosGrupoService {

    private static final Duration TIMEOUT = Duration.ofMinutes(30);
    private static final String EVENTO_CONECTADO = "CONECTADO";

    private final GrupoService grupoService;

    private final InscricoesSse inscricoes = new InscricoesSse();

    public SseEmitter inscrever(UUID grupoId, UUID usuarioId) {
        grupoService.exigirMembro(grupoId, usuarioId);

        SseEmitter emitter = new SseEmitter(TIMEOUT.toMillis());
        inscricoes.adicionar(grupoId, emitter);

        // Primeiro evento: envia os headers da resposta e confirma a conexão ao cliente
        inscricoes.enviar(grupoId, emitter, SseEmitter.event().name(EVENTO_CONECTADO).data(EVENTO_CONECTADO));
        log.debug("Usuário {} conectado aos eventos do grupo {}", usuarioId, grupoId);
        return emitter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void aoAlterar(GrupoAlteradoEvent evento) {
        String tipo = evento.tipo().name();
        inscricoes.enviarATodos(evento.grupoId(), () -> SseEmitter.event().name(tipo).data(tipo));
    }

    @Scheduled(fixedRate = 25_000)
    public void manterConexoes() {
        inscricoes.manterConexoes();
    }
}
