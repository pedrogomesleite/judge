package com.packges.runner.sandbox;

import com.packges.runner.config.RunnerProperties;
import com.packges.runner.exception.FalhaInfraestruturaException;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Ids de box do isolate livres nesta réplica (0 a boxes - 1): cada execução simultânea usa um box próprio (ADR-002).
 * Como a concorrência do listener é igual ao número de boxes, sempre há um livre para cada mensagem.
 */
@Component
public class PoolDeBoxes {

    private final BlockingQueue<Integer> livres;

    public PoolDeBoxes(RunnerProperties properties) {
        this.livres = new ArrayBlockingQueue<>(properties.boxes());
        for (int box = 0; box < properties.boxes(); box++) {
            livres.add(box);
        }
    }

    public int emprestar() {
        try {
            return livres.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FalhaInfraestruturaException("Interrompido esperando um box livre", e);
        }
    }

    public void devolver(int box) {
        livres.add(box);
    }
}
