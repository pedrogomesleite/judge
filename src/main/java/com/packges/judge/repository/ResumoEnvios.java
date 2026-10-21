package com.packges.judge.repository;

import java.util.UUID;

/**
 * Envios de um autor para uma atividade dentro de um grupo (projeção de {@link SolucaoRepository#resumirPorAutorEAtividade}).
 */
public interface ResumoEnvios {

    UUID getAutorId();

    UUID getAtividadeId();

    long getTentativas();

    long getAceitas();
}
