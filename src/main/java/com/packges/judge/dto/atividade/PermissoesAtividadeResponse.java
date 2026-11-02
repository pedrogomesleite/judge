package com.packges.judge.dto.atividade;

/**
 * O que o usuário logado pode fazer com a atividade. Os motivos são nulos quando a ação é permitida.
 */
public record PermissoesAtividadeResponse(
        boolean podeEditar,
        String motivoEdicao,
        boolean podeExcluir,
        String motivoExclusao
) {
}
