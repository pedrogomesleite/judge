package com.packges.judge.repository;

import com.packges.judge.entity.MembroGrupo;
import com.packges.judge.enums.PapelGrupo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembroGrupoRepository extends JpaRepository<MembroGrupo, UUID> {

    Optional<MembroGrupo> findByGrupoIdAndUsuarioId(UUID grupoId, UUID usuarioId);

    boolean existsByGrupoIdAndUsuarioId(UUID grupoId, UUID usuarioId);

    List<MembroGrupo> findByGrupoId(UUID grupoId);

    List<MembroGrupo> findByUsuarioId(UUID usuarioId);

    boolean existsByUsuarioIdAndPapel(UUID usuarioId, PapelGrupo papel);

    boolean existsByGrupoIdAndPapel(UUID grupoId, PapelGrupo papel);

    /** Exclusão do grupo. */
    @Modifying
    @Query("delete from MembroGrupo m where m.grupo.id = :grupoId")
    int deleteByGrupoId(UUID grupoId);
}
