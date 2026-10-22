package com.packges.judge.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param timeoutConfirmacao espera máxima pelo publisher confirm; sem ele, a solução fica para a reconciliação.
 */
@ConfigurationProperties("juizin.mensageria")
public record MensageriaProperties(
        Duration timeoutConfirmacao
) {
    public MensageriaProperties {
        if (timeoutConfirmacao == null) {
            timeoutConfirmacao = Duration.ofSeconds(5);
        }
    }
}
