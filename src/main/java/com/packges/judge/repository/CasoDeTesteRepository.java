package com.packges.judge.repository;

import com.packges.judge.entity.CasoDeTeste;
import com.packges.judge.enums.TipoCasoDeTeste;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CasoDeTesteRepository extends JpaRepository<CasoDeTeste, UUID> {

    Optional<CasoDeTeste> findByIdAndAtividadeId(UUID id, UUID atividadeId);

    List<CasoDeTeste> findByAtividadeIdAndTipo(UUID atividadeId, TipoCasoDeTeste tipo);

    /** Só os ids, sem carregar a entrada e a saída esperada (pedido de execução enviado ao runner). */
    @Query("select c.id from CasoDeTeste c where c.atividade.id = :atividadeId and c.tipo = :tipo")
    List<UUID> findIdsByAtividadeIdAndTipo(UUID atividadeId, TipoCasoDeTeste tipo);

    List<CasoDeTeste> findByAtividadeIdAndGrupoId(UUID atividadeId, UUID grupoId);

    List<CasoDeTeste> findByAtividadeIdAndTipoAndPublicoTrue(UUID atividadeId, TipoCasoDeTeste tipo);

    @EntityGraph(attributePaths = "grupo")
    List<CasoDeTeste> findByAtividadeIdAndTipoAndGrupoIdIn(UUID atividadeId, TipoCasoDeTeste tipo, List<UUID> grupoIds);

    /** Oficiais e comunitários (exclusão da atividade): apague antes os resultados que apontam para eles. */
    @Modifying
    @Query("delete from CasoDeTeste c where c.atividade.id = :atividadeId")
    int deleteByAtividadeId(UUID atividadeId);

    /** Casos comunitários do grupo (exclusão do grupo). Os oficiais não têm grupo e continuam na atividade. */
    @Modifying
    @Query("delete from CasoDeTeste c where c.grupo.id = :grupoId")
    int deleteByGrupoId(UUID grupoId);
}
