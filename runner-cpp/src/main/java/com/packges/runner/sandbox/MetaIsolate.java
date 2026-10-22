package com.packges.runner.sandbox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Meta-arquivo do isolate ({@code chave:valor} por linha) de uma execução (ADR-002).
 *
 * @param status    nulo se o programa terminou com código 0; senão RE (código diferente de 0), SG (sinal),
 *                  TO (tempo excedido) ou XX (erro interno do sandbox).
 * @param tempoMs   tempo de CPU.
 * @param memoriaKb pico de memória do cgroup (cg-mem) ou, sem ele, o max-rss.
 * @param oomKilled o programa foi morto por exceder o limite de memória do cgroup.
 * @param mensagem  descrição do isolate, só para log.
 */
public record MetaIsolate(
        String status,
        int tempoMs,
        int memoriaKb,
        boolean oomKilled,
        String mensagem
) {
    public static final String STATUS_TEMPO_EXCEDIDO = "TO";
    public static final String STATUS_ERRO_INTERNO = "XX";

    public static MetaIsolate ler(List<String> linhas) {
        Map<String, String> valores = new HashMap<>();
        for (String linha : linhas) {
            int separador = linha.indexOf(':');
            if (separador > 0) {
                valores.put(linha.substring(0, separador), linha.substring(separador + 1).strip());
            }
        }

        String tempo = valores.get("time");
        String memoria = valores.getOrDefault("cg-mem", valores.get("max-rss"));
        return new MetaIsolate(
                valores.get("status"),
                tempo != null ? (int) Math.round(Double.parseDouble(tempo) * 1000) : 0,
                memoria != null ? Integer.parseInt(memoria) : 0,
                valores.containsKey("cg-oom-killed"),
                valores.get("message")
        );
    }

    public boolean sucesso() {
        return status == null;
    }
}
