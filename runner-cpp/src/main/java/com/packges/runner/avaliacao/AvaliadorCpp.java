package com.packges.runner.avaliacao;

import com.packges.runner.casos.CasoDeTeste;
import com.packges.runner.casos.CasosDeTesteClient;
import com.packges.runner.config.RunnerProperties;
import com.packges.runner.exception.FalhaInfraestruturaException;
import com.packges.runner.mensageria.PedidoExecucao;
import com.packges.runner.mensageria.ResultadoExecucao;
import com.packges.runner.mensageria.Veredito;
import com.packges.runner.sandbox.Execucao;
import com.packges.runner.sandbox.Isolate;
import com.packges.runner.sandbox.MetaIsolate;
import com.packges.runner.sandbox.PoolDeBoxes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Compila a solução C++ e a executa contra os casos OFICIAL, tudo dentro do isolate (ADR-002).
 * <p>
 * Os casos rodam em ordem e param no primeiro que falha, para poupar a CPU do runner, que é o gargalo (ADR-006).
 * O veredito é o do primeiro caso que falhou (ou ACEITA). O tempo e a memória são os maiores entre os casos executados.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AvaliadorCpp {

    private static final String CODIGO = "solucao.cpp";
    private static final String EXECUTAVEL = "solucao";
    private static final String SAIDA_COMPILACAO = "compilacao.txt";
    private static final String ENTRADA = "entrada.txt";
    private static final String SAIDA = "saida.txt";
    private static final String ERROS = "erros.txt";

    private final Isolate isolate;
    private final PoolDeBoxes boxes;
    private final CasosDeTesteClient casosDeTesteClient;
    private final RunnerProperties properties;

    public ResultadoExecucao avaliar(PedidoExecucao pedido) {
        List<CasoDeTeste> casos = casosDeTesteClient.obter(pedido.atividadeId(), pedido.casosDeTesteIds());

        int box = boxes.emprestar();
        try {
            Path diretorio = isolate.iniciar(box);
            Files.writeString(diretorio.resolve(CODIGO), pedido.codigo(), StandardCharsets.UTF_8);

            Optional<Veredito> falhaCompilacao = ClassificadorExecucao.compilacao(isolate.rodar(box, compilacao()));
            if (falhaCompilacao.isPresent()) {
                return new ResultadoExecucao(pedido.solucaoId(), falhaCompilacao.get(), null, null, List.of());
            }
            return executar(pedido, casos, box, diretorio);
        } catch (IOException e) {
            throw new FalhaInfraestruturaException("Falha ao usar o box " + box, e);
        } finally {
            isolate.limpar(box);
            boxes.devolver(box);
        }
    }

    private ResultadoExecucao executar(PedidoExecucao pedido, List<CasoDeTeste> casos, int box, Path diretorio)
            throws IOException {
        Execucao execucao = execucao(pedido);
        List<ResultadoExecucao.ResultadoCaso> resultados = new ArrayList<>();
        Veredito veredito = Veredito.ACEITA;
        int tempoMs = 0;
        int memoriaKb = 0;

        for (CasoDeTeste caso : casos) {
            Files.writeString(diretorio.resolve(ENTRADA), caso.entrada(), StandardCharsets.UTF_8);
            MetaIsolate meta = isolate.rodar(box, execucao);

            Veredito doCaso = ClassificadorExecucao.execucao(meta).orElse(null);
            if (doCaso == null) {
                // Bytes inválidos em UTF-8 viram caracteres de substituição, e a comparação falha normalmente
                String obtida = new String(Files.readAllBytes(diretorio.resolve(SAIDA)), StandardCharsets.UTF_8);
                doCaso = ComparadorSaida.iguais(obtida, caso.saidaEsperada()) ? Veredito.ACEITA : Veredito.RESPOSTA_ERRADA;
            }

            resultados.add(new ResultadoExecucao.ResultadoCaso(caso.id(), doCaso, meta.tempoMs(), meta.memoriaKb()));
            tempoMs = Math.max(tempoMs, meta.tempoMs());
            memoriaKb = Math.max(memoriaKb, meta.memoriaKb());
            if (doCaso != Veredito.ACEITA) {
                log.debug("Solução {} falhou no caso {}: {} ({})", pedido.solucaoId(), caso.id(), doCaso, meta.mensagem());
                veredito = doCaso;
                break;
            }
        }
        return new ResultadoExecucao(pedido.solucaoId(), veredito, tempoMs, memoriaKb, resultados);
    }

    /** -static: o executável não depende das bibliotecas do sistema dentro do sandbox. */
    private Execucao compilacao() {
        return new Execucao(
                List.of(properties.compilador(), "-std=c++17", "-O2", "-pipe", "-static", "-o", EXECUTAVEL, CODIGO),
                properties.tempoCompilacao(),
                properties.tempoCompilacao().multipliedBy(2),
                properties.memoriaCompilacaoMb() * 1024,
                true,
                null,
                SAIDA_COMPILACAO,
                null
        );
    }

    /** Limites da atividade; o tempo de relógio folgado só pega programas parados (ex.: esperando entrada). */
    private Execucao execucao(PedidoExecucao pedido) {
        Duration tempo = Duration.ofMillis(pedido.limiteTempoMs());
        return new Execucao(
                List.of("./" + EXECUTAVEL),
                tempo,
                tempo.multipliedBy(2).plusSeconds(1),
                pedido.limiteMemoriaMb() * 1024,
                false,
                ENTRADA,
                SAIDA,
                ERROS
        );
    }
}
