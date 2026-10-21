package com.packges.judge.enums;

/**
 * O que mudou em um grupo. É o nome do evento enviado pelo SSE do grupo: o cliente busca de novo o que for afetado.
 */
public enum TipoAlteracaoGrupo {
    /** Dados ou status do grupo. */
    GRUPO,
    /** Atividades do grupo ou seus pesos. */
    ATIVIDADES,
    /** Entrada, promoção ou remoção de membros. */
    MEMBROS,
    /** Envio de solução no grupo ou mudança no status/veredito dela. */
    SOLUCOES
}
