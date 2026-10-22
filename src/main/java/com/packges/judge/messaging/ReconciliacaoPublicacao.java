package com.packges.judge.messaging;

import com.packges.judge.service.SolucaoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Rede de segurança da publicação (ADR-007): republica as soluções PENDENTE sem publicadaEm, enviadas há mais tempo que
 * a idade mínima. Cobre o broker fora do ar e a queda da API entre o commit e a publicação. Também roda logo depois do
 * startup.
 * <p>
 * Com várias réplicas da API, o job precisará rodar em uma única instância ou usar SELECT ... FOR UPDATE SKIP LOCKED.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(ReconciliacaoProperties.class)
public class ReconciliacaoPublicacao {

    private final SolucaoService solucaoService;
    private final PublicadorSolucoes publicador;
    private final ReconciliacaoProperties properties;

    @Scheduled(fixedDelayString = "${juizin.reconciliacao.intervalo:PT30S}")
    public void republicar() {
        Instant enviadaAntesDe = Instant.now().minus(properties.idadeMinima());
        List<UUID> naoPublicadas = solucaoService.listarNaoPublicadas(enviadaAntesDe, properties.lote());
        if (naoPublicadas.isEmpty()) {
            return;
        }

        log.info("Reconciliação: {} solução(ões) sem publicação", naoPublicadas.size());
        for (UUID solucaoId : naoPublicadas) {
            // Com o broker fora do ar, as demais também falhariam: tenta de novo na próxima execução
            if (!publicador.publicar(solucaoId)) {
                return;
            }
        }
    }
}
