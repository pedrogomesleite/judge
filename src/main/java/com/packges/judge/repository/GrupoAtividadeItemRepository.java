package com.packges.judge.repository;

import com.packges.judge.entity.GrupoAtividadeItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GrupoAtividadeItemRepository extends JpaRepository<GrupoAtividadeItem, UUID> {

    boolean existsByGrupoIdAndAtividadeId(UUID grupoId, UUID atividadeId);

    Optional<GrupoAtividadeItem> findByGrupoIdAndAtividadeId(UUID grupoId, UUID atividadeId);

    List<GrupoAtividadeItem> findByGrupoIdOrderByOrdem(UUID grupoId);

    long deleteByGrupoIdAndAtividadeId(UUID grupoId, UUID atividadeId);
}
