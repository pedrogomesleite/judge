package com.packges.judge.service;

import com.packges.judge.dto.solucao.SolucaoResponse;
import com.packges.judge.enums.StatusSolucao;
import com.packges.judge.messaging.SolucaoAlteradaEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.UUID;

/**
 * SSE por solução (ADR-003): envia o estado da solução a cada mudança de status (nome do evento = status, dado =
 * SolucaoResponse) e encerra o stream quando ela termina (CONCLUIDA ou FALHA). O primeiro evento é o estado atual,
 * então uma reconexão já recebe o que se perdeu. Valem as mesmas regras de visibilidade do GET da solução.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventosSolucaoService {

    private static final Duration TIMEOUT = Duration.ofMinutes(10);

    private final SolucaoService solucaoService;

    private final InscricoesSse inscricoes = new InscricoesSse();

    public SseEmitter inscrever(UUID solucaoId, UUID usuarioId) {
        // Verifica a permissão antes de abrir o stream, para os erros (403/404) chegarem como ErroResponse
        solucaoService.buscar(solucaoId, usuarioId);

        SseEmitter emitter = new SseEmitter(TIMEOUT.toMillis());
        inscricoes.adicionar(solucaoId, emitter);

        // Lê o estado só depois de inscrever, para não perder uma mudança entre a leitura e a inscrição.
        // Se a mudança chegar antes deste envio, o cliente ignora o status que regride.
        SolucaoResponse atual = solucaoService.buscar(solucaoId, usuarioId);
        if (inscricoes.enviar(solucaoId, emitter, evento(atual)) && finalizada(atual)) {
            emitter.complete();
        }
        log.debug("Usuário {} acompanhando a solução {}", usuarioId, solucaoId);
        return emitter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void aoAlterar(SolucaoAlteradaEvent evento) {
        SolucaoResponse solucao = evento.solucao();
        inscricoes.enviarATodos(solucao.id(), () -> evento(solucao));
        if (finalizada(solucao)) {
            inscricoes.encerrar(solucao.id());
        }
    }

    @Scheduled(fixedRate = 25_000)
    public void manterConexoes() {
        inscricoes.manterConexoes();
    }

    private static SseEmitter.SseEventBuilder evento(SolucaoResponse solucao) {
        return SseEmitter.event().name(solucao.status().name()).data(solucao, MediaType.APPLICATION_JSON);
    }

    private static boolean finalizada(SolucaoResponse solucao) {
        return solucao.status() == StatusSolucao.CONCLUIDA || solucao.status() == StatusSolucao.FALHA;
    }
}
