package com.packges.judge.messaging;

import com.packges.judge.dto.solucao.ResultadoExecucao;
import com.packges.judge.enums.TipoCasoDeTeste;
import com.packges.judge.enums.Veredito;
import com.packges.judge.repository.CasoDeTesteRepository;
import com.packges.judge.service.SolucaoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simula o runner enquanto a integração com o RabbitMQ é TODO (ADR-006, ADR-007).
 * <p>
 * Depois do commit, passa a solução para EM_EXECUCAO e, em seguida, registra um veredito fictício:
 * ACEITA por padrão, ou o veredito indicado pela diretiva {@code juizin-mock: <VEREDITO>} no código.
 * Desligado com {@code juizin.runner.mock.enabled=false}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(RunnerMockProperties.class)
@ConditionalOnProperty(name = "juizin.runner.mock.enabled", havingValue = "true")
public class RunnerSimulado {

    private static final Pattern DIRETIVA = Pattern.compile("juizin-mock:\\s*([A-Z_]+)");
    private static final int TEMPO_MS_SIMULADO = 12;
    private static final int MEMORIA_KB_SIMULADA = 3072;

    private final SolucaoService solucaoService;
    private final CasoDeTesteRepository casoDeTesteRepository;
    private final TaskScheduler taskScheduler;
    private final RunnerMockProperties properties;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoEnviar(SolucaoEnviadaEvent evento) {
        log.info("[mock] Solução {} recebida pelo runner simulado (fila {})", evento.solucaoId(), evento.fila());
        Instant agora = Instant.now();
        taskScheduler.schedule(() -> iniciarExecucao(evento.solucaoId()), agora.plus(properties.atraso()));
        taskScheduler.schedule(() -> concluir(evento), agora.plus(properties.atraso().multipliedBy(2)));
    }

    private void iniciarExecucao(UUID solucaoId) {
        log.info("[mock] Solução {} em execução", solucaoId);
        solucaoService.marcarEmExecucao(solucaoId);
    }

    private void concluir(SolucaoEnviadaEvent evento) {
        ResultadoExecucao resultado = simular(evento);
        log.info("[mock] Solução {} concluída com veredito {}", evento.solucaoId(), resultado.veredito());
        solucaoService.registrarResultado(resultado);
    }

    private ResultadoExecucao simular(SolucaoEnviadaEvent evento) {
        Veredito veredito = lerDiretiva(evento.codigo());
        if (veredito == Veredito.ERRO_COMPILACAO) {
            return new ResultadoExecucao(evento.solucaoId(), veredito, null, null, List.of());
        }

        List<ResultadoExecucao.ResultadoCaso> casos = casoDeTesteRepository
                .findByAtividadeIdAndTipo(evento.atividadeId(), TipoCasoDeTeste.OFICIAL).stream()
                .map(caso -> new ResultadoExecucao.ResultadoCaso(caso.getId(), veredito, TEMPO_MS_SIMULADO, MEMORIA_KB_SIMULADA))
                .toList();
        return new ResultadoExecucao(evento.solucaoId(), veredito, TEMPO_MS_SIMULADO, MEMORIA_KB_SIMULADA, casos);
    }

    private Veredito lerDiretiva(String codigo) {
        Matcher matcher = DIRETIVA.matcher(codigo);
        if (!matcher.find()) {
            return Veredito.ACEITA;
        }
        try {
            return Veredito.valueOf(matcher.group(1));
        } catch (IllegalArgumentException e) {
            log.warn("[mock] Diretiva inválida '{}', usando ACEITA", matcher.group(1));
            return Veredito.ACEITA;
        }
    }
}
