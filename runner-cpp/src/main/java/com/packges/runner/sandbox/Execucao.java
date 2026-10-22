package com.packges.runner.sandbox;

import java.time.Duration;
import java.util.List;

/**
 * Uma chamada de {@code isolate --run}. Os arquivos são relativos ao box.
 *
 * @param comando         programa e argumentos, como vistos de dentro do sandbox.
 * @param tempo           limite de tempo de CPU.
 * @param tempoParede     limite de tempo de relógio (pega programas parados, ex.: esperando entrada).
 * @param memoriaKb       limite de memória do cgroup.
 * @param variosProcessos libera processos filhos (o g++ chama cc1plus, as e ld).
 * @param entrada         arquivo usado como stdin, ou nulo.
 * @param saida           arquivo que recebe o stdout.
 * @param erros           arquivo que recebe o stderr; nulo junta o stderr ao stdout.
 */
public record Execucao(
        List<String> comando,
        Duration tempo,
        Duration tempoParede,
        int memoriaKb,
        boolean variosProcessos,
        String entrada,
        String saida,
        String erros
) {
}
