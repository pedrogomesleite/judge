package com.packges.judge.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("juizin.runner.mock")
public record RunnerMockProperties(
        boolean enabled,
        Duration atraso
) {
    public RunnerMockProperties {
        if (atraso == null) {
            atraso = Duration.ofSeconds(2);
        }
    }
}
