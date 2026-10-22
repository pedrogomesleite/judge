package com.packges.runner.sandbox;

import com.packges.runner.config.RunnerProperties;
import com.packges.runner.exception.FalhaInfraestruturaException;
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
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Chama o isolate (sandbox de cada compilação e execução, ADR-002), sempre com cgroups (--cg).
 * <p>
 * O isolate é setuid root e o runner roda sem root (ADR-005). Entre as chamadas, o box pertence ao usuário do runner,
 * que grava ali o código e as entradas e lê as saídas.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Isolate {

    /** Folga sobre o limite de relógio para o próprio isolate encerrar o programa e responder. */
    private static final Duration FOLGA = Duration.ofSeconds(10);
    private static final Duration TIMEOUT_GERENCIA = Duration.ofSeconds(30);
    /** Código de saída do isolate acima do qual a falha é do sandbox, e não do programa (0 = ok, 1 = falhou). */
    private static final int CODIGO_PROGRAMA_FALHOU = 1;

    private final RunnerProperties properties;

    /** Prepara um box limpo e devolve o diretório de trabalho dele (visto como /box de dentro do sandbox). */
    public Path iniciar(int box) {
        // O --init já descarta o que tiver sobrado de uma execução interrompida
        Chamada chamada = chamar(List.of("--cg", "--box-id=" + box, "--init"), TIMEOUT_GERENCIA);
        if (chamada.codigo() != 0) {
            throw new FalhaInfraestruturaException("isolate --init falhou no box %d: %s".formatted(box, chamada.saida()));
        }
        List<String> linhas = chamada.saida().lines().filter(linha -> !linha.isBlank()).toList();
        if (linhas.isEmpty()) {
            throw new FalhaInfraestruturaException("isolate --init não informou o diretório do box " + box);
        }
        return Path.of(linhas.getLast().strip(), "box");
    }

    public MetaIsolate rodar(int box, Execucao execucao) {
        Path meta = null;
        try {
            meta = Files.createTempFile("isolate-" + box + "-", ".meta");
            Chamada chamada = chamar(argumentos(box, execucao, meta), execucao.tempoParede().plus(FOLGA));
            if (chamada.codigo() > CODIGO_PROGRAMA_FALHOU) {
                throw new FalhaInfraestruturaException("isolate --run falhou no box %d (código %d): %s"
                        .formatted(box, chamada.codigo(), chamada.saida()));
            }
            return MetaIsolate.ler(Files.readAllLines(meta));
        } catch (IOException e) {
            throw new FalhaInfraestruturaException("Falha ao ler o meta-arquivo do box " + box, e);
        } finally {
            apagar(meta);
        }
    }

    /** Remove o box. Não lança exceção: o próximo --init limpa o que sobrar. */
    public void limpar(int box) {
        try {
            Chamada chamada = chamar(List.of("--cg", "--box-id=" + box, "--cleanup"), TIMEOUT_GERENCIA);
            if (chamada.codigo() != 0) {
                log.warn("isolate --cleanup falhou no box {}: {}", box, chamada.saida());
            }
        } catch (FalhaInfraestruturaException e) {
            log.warn("isolate --cleanup falhou no box {}", box, e);
        }
    }

    private List<String> argumentos(int box, Execucao execucao, Path meta) {
        List<String> argumentos = new ArrayList<>(List.of(
                "--cg",
                "--box-id=" + box,
                "--meta=" + meta,
                "--time=" + segundos(execucao.tempo()),
                "--wall-time=" + segundos(execucao.tempoParede()),
                "--cg-mem=" + execucao.memoriaKb(),
                "--fsize=" + properties.tamanhoMaximoArquivoKb(),
                "--env=PATH=/usr/bin:/bin"
        ));
        if (execucao.variosProcessos()) {
            argumentos.add("--processes");
        }
        if (execucao.entrada() != null) {
            argumentos.add("--stdin=" + execucao.entrada());
        }
        argumentos.add("--stdout=" + execucao.saida());
        argumentos.add(execucao.erros() != null ? "--stderr=" + execucao.erros() : "--stderr-to-stdout");
        argumentos.add("--run");
        argumentos.add("--");
        argumentos.addAll(execucao.comando());
        return argumentos;
    }

    /**
     * A saída do isolate vai para um arquivo, e não para um pipe: assim, a espera pelo processo nunca trava lendo
     * a saída.
     */
    private Chamada chamar(List<String> argumentos, Duration timeout) {
        List<String> comando = new ArrayList<>();
        comando.add(properties.isolate());
        comando.addAll(argumentos);

        Path saida = null;
        try {
            saida = Files.createTempFile("isolate-", ".log");
            Process processo = new ProcessBuilder(comando)
                    .redirectErrorStream(true)
                    .redirectOutput(saida.toFile())
                    .start();
            if (!processo.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                processo.destroyForcibly();
                throw new FalhaInfraestruturaException("isolate não respondeu em " + timeout + ": " + comando);
            }
            return new Chamada(processo.exitValue(), Files.readString(saida, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new FalhaInfraestruturaException("Falha ao chamar o isolate: " + comando, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FalhaInfraestruturaException("Interrompido esperando o isolate", e);
        } finally {
            apagar(saida);
        }
    }

    private static String segundos(Duration duracao) {
        return String.format(Locale.ROOT, "%.3f", duracao.toMillis() / 1000.0);
    }

    private static void apagar(Path arquivo) {
        if (arquivo == null) {
            return;
        }
        try {
            Files.deleteIfExists(arquivo);
        } catch (IOException e) {
            log.warn("Não foi possível apagar {}", arquivo, e);
        }
    }

    private record Chamada(int codigo, String saida) {
    }
}
