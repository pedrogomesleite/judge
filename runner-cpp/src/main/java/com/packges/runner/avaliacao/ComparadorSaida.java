package com.packges.runner.avaliacao;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Compara a saída obtida com a esperada, ignorando os espaços no fim de cada linha, as linhas em branco no final e a
 * diferença entre \r\n e \n (docs/architecture.md, 3.4).
 */
public final class ComparadorSaida {

    private ComparadorSaida() {
    }

    public static boolean iguais(String obtida, String esperada) {
        return normalizar(obtida).equals(normalizar(esperada));
    }

    static String normalizar(String texto) {
        List<String> linhas = texto.lines()
                .map(String::stripTrailing)
                .collect(Collectors.toCollection(ArrayList::new));
        while (!linhas.isEmpty() && linhas.getLast().isEmpty()) {
            linhas.removeLast();
        }
        return String.join("\n", linhas);
    }
}
