package com.packges.judge.repository;

import com.packges.judge.entity.CasoDeTeste;
import com.packges.judge.enums.TipoCasoDeTeste;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CasoDeTesteRepository extends JpaRepository<CasoDeTeste, UUID> {

    Optional<CasoDeTeste> findByIdAndAtividadeId(UUID id, UUID atividadeId);

    List<CasoDeTeste> findByAtividadeIdAndTipo(UUID atividadeId, TipoCasoDeTeste tipo);

    List<CasoDeTeste> findByAtividadeIdAndGrupoId(UUID atividadeId, UUID grupoId);

    List<CasoDeTeste> findByAtividadeIdAndTipoAndPublicoTrue(UUID atividadeId, TipoCasoDeTeste tipo);

    @EntityGraph(attributePaths = "grupo")
    List<CasoDeTeste> findByAtividadeIdAndTipoAndGrupoIdIn(UUID atividadeId, TipoCasoDeTeste tipo, List<UUID> grupoIds);
}
