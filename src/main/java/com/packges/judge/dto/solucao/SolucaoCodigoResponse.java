package com.packges.judge.dto.solucao;

import com.packges.judge.entity.Solucao;

import java.util.Objects;
import java.util.UUID;

/**
 * Código enviado numa solução. Fica fora do SolucaoResponse para as listagens não carregarem o código.
 *
 * @param extensao extensão do arquivo na linguagem da solução, sem o ponto (ex.: cpp).
 */
public record SolucaoCodigoResponse(
        UUID id,
        String linguagem,
        String extensao,
        String codigo
) {
    private static final String EXTENSAO_PADRAO = "txt";

    public static SolucaoCodigoResponse from(Solucao solucao) {
        return new SolucaoCodigoResponse(
                solucao.getId(),
                solucao.getLinguagem().getNome(),
                Objects.requireNonNullElse(solucao.getLinguagem().getExtensao(), EXTENSAO_PADRAO),
                solucao.getCodigo()
        );
    }
}
