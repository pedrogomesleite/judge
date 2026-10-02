package com.packges.judge.repository;

import com.packges.judge.entity.Solucao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SolucaoRepository extends JpaRepository<Solucao, UUID> {

    List<Solucao> findByAutorIdOrderByEnviadoEmDesc(UUID autorId);

    List<Solucao> findByGrupoIdOrderByEnviadoEmDesc(UUID grupoId);

    List<Solucao> findByGrupoIdAndAutorIdOrderByEnviadoEmDesc(UUID grupoId, UUID autorId);
}
