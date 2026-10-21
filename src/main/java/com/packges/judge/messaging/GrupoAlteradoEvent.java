package com.packges.judge.messaging;

import com.packges.judge.enums.TipoAlteracaoGrupo;

import java.util.UUID;

/**
 * Emitido quando algo muda em um grupo. Depois do commit, o {@code EventosGrupoService} avisa os membros conectados
 * pelo SSE do grupo. Não leva dados: quem recebe busca de novo pelos endpoints, que aplicam as permissões.
 */
public record GrupoAlteradoEvent(
        UUID grupoId,
        TipoAlteracaoGrupo tipo
) {
}
