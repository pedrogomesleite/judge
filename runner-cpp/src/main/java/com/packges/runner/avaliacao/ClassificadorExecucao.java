package com.packges.runner.avaliacao;

import com.packges.runner.mensageria.Veredito;
import com.packges.runner.sandbox.MetaIsolate;

import java.util.Optional;

/**
 * Converte o meta-arquivo do isolate em veredito (ADR-002). Quando o programa termina bem, quem decide é a comparação
 * da saída ({@link ComparadorSaida}).
 */
public final class ClassificadorExecucao {

    private ClassificadorExecucao() {
    }

    /** Veredito da compilação que falhou; vazio se o código compilou. */
    public static Optional<Veredito> compilacao(MetaIsolate meta) {
        if (meta.sucesso()) {
            return Optional.empty();
        }
        return Optional.of(MetaIsolate.STATUS_ERRO_INTERNO.equals(meta.status())
                ? Veredito.ERRO_INTERNO
                : Veredito.ERRO_COMPILACAO);
    }

    /** Veredito da execução que falhou; vazio se o programa terminou bem e a saída deve ser comparada. */
    public static Optional<Veredito> execucao(MetaIsolate meta) {
        // Vem antes do status: o programa morto pelo OOM killer aparece como SG (sinal)
        if (meta.oomKilled()) {
            return Optional.of(Veredito.MEMORIA_EXCEDIDA);
        }
        if (meta.sucesso()) {
            return Optional.empty();
        }
        return Optional.of(switch (meta.status()) {
            case MetaIsolate.STATUS_TEMPO_EXCEDIDO -> Veredito.TEMPO_EXCEDIDO;
            case MetaIsolate.STATUS_ERRO_INTERNO -> Veredito.ERRO_INTERNO;
            // RE (código de saída diferente de 0) e SG (sinal, ex.: falha de segmentação)
            default -> Veredito.ERRO_EXECUCAO;
        });
    }
}
