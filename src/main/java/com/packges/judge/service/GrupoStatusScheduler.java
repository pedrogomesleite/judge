package com.packges.judge.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Inicia e finaliza grupos automaticamente com base em dataInicio/dataFim.
 */
@Component
@RequiredArgsConstructor
public class GrupoStatusScheduler {

    private final GrupoService grupoService;

    @Scheduled(fixedDelay = 60_000)
    public void atualizarStatus() {
        grupoService.atualizarStatusPorData(Instant.now());
    }
}
