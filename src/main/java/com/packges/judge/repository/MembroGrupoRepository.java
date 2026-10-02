package com.packges.judge.repository;

import com.packges.judge.entity.MembroGrupo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembroGrupoRepository extends JpaRepository<MembroGrupo, UUID> {

    Optional<MembroGrupo> findByGrupoIdAndUsuarioId(UUID grupoId, UUID usuarioId);

    boolean existsByGrupoIdAndUsuarioId(UUID grupoId, UUID usuarioId);

    List<MembroGrupo> findByGrupoId(UUID grupoId);

    List<MembroGrupo> findByUsuarioId(UUID usuarioId);
}
