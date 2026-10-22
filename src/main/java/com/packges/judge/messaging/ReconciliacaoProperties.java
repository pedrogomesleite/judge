package com.packges.judge.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * O intervalo do job fica em {@code juizin.reconciliacao.intervalo}, lido direto pelo {@code @Scheduled}.
 *
 * @param idadeMinima só republica soluções enviadas há mais tempo que isso, para não concorrer com a publicação normal.
 * @param lote        máximo de soluções republicadas por execução.
 */
@ConfigurationProperties("juizin.reconciliacao")
public record ReconciliacaoProperties(
        Duration idadeMinima,
        Integer lote
) {
    public ReconciliacaoProperties {
        if (idadeMinima == null) {
            idadeMinima = Duration.ofSeconds(30);
        }
        if (lote == null) {
            lote = 100;
        }
    }
}
