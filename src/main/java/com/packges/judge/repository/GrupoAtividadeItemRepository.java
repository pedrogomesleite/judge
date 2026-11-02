package com.packges.judge.repository;

import com.packges.judge.entity.GrupoAtividadeItem;
import com.packges.judge.enums.StatusGrupo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GrupoAtividadeItemRepository extends JpaRepository<GrupoAtividadeItem, UUID> {

    boolean existsByGrupoIdAndAtividadeId(UUID grupoId, UUID atividadeId);

    Optional<GrupoAtividadeItem> findByGrupoIdAndAtividadeId(UUID grupoId, UUID atividadeId);

    List<GrupoAtividadeItem> findByGrupoIdOrderByOrdem(UUID grupoId);

    long deleteByGrupoIdAndAtividadeId(UUID grupoId, UUID atividadeId);

    boolean existsByAtividadeId(UUID atividadeId);

    boolean existsByAtividadeIdAndGrupoStatusIn(UUID atividadeId, Collection<StatusGrupo> status);

    /** Exclusão do grupo: tira as atividades dele, sem apagar as atividades. */
    @Modifying
    @Query("delete from GrupoAtividadeItem i where i.grupo.id = :grupoId")
    int deleteByGrupoId(UUID grupoId);
}
