package com.packges.judge.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Conexões SSE abertas, agrupadas por chave (o id do grupo ou da solução acompanhada), para os serviços de eventos
 * (ADR-003). A inscrição sai sozinha quando a conexão termina, expira ou falha no envio.
 * <p>
 * As inscrições ficam em memória: com várias réplicas da API, os eventos precisarão ser distribuídos entre elas
 * (ver ADR-003).
 */
class InscricoesSse {

    private final Map<UUID, Set<SseEmitter>> inscricoes = new ConcurrentHashMap<>();

    void adicionar(UUID chave, SseEmitter emitter) {
        // compute é atômico por chave: não concorre com a remoção do conjunto vazio em remover()
        inscricoes.compute(chave, (id, daChave) -> {
            Set<SseEmitter> conjunto = daChave != null ? daChave : ConcurrentHashMap.newKeySet();
            conjunto.add(emitter);
            return conjunto;
        });

        emitter.onCompletion(() -> remover(chave, emitter));
        emitter.onTimeout(() -> remover(chave, emitter));
        emitter.onError(erro -> remover(chave, emitter));
    }

    /**
     * @return false se o cliente já tinha desconectado (a inscrição é removida).
     */
    boolean enviar(UUID chave, SseEmitter emitter, SseEmitter.SseEventBuilder evento) {
        try {
            emitter.send(evento);
            return true;
        } catch (IOException | IllegalStateException e) {
            // Cliente desconectado ou emitter já encerrado
            remover(chave, emitter);
            return false;
        }
    }

    /** Um evento novo por inscrito: o SseEventBuilder não pode ser reaproveitado entre envios. */
    void enviarATodos(UUID chave, Supplier<SseEmitter.SseEventBuilder> evento) {
        Set<SseEmitter> daChave = inscricoes.get(chave);
        if (daChave != null) {
            daChave.forEach(emitter -> enviar(chave, emitter, evento.get()));
        }
    }

    /** Encerra os streams da chave; o cliente recebe o fim normal da resposta. */
    void encerrar(UUID chave) {
        Set<SseEmitter> daChave = inscricoes.remove(chave);
        if (daChave != null) {
            daChave.forEach(SseEmitter::complete);
        }
    }

    /** Comentário periódico: mantém a conexão aberta em proxies que encerram conexões ociosas. */
    void manterConexoes() {
        inscricoes.forEach((chave, daChave) ->
                daChave.forEach(emitter -> enviar(chave, emitter, SseEmitter.event().comment("ping"))));
    }

    private void remover(UUID chave, SseEmitter emitter) {
        inscricoes.computeIfPresent(chave, (id, daChave) -> {
            daChave.remove(emitter);
            return daChave.isEmpty() ? null : daChave;
        });
    }
}
