package com.packges.judge.repository;

import com.packges.judge.entity.CasoDeTeste;
import com.packges.judge.enums.TipoCasoDeTeste;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CasoDeTesteRepository extends JpaRepository<CasoDeTeste, UUID> {

    List<CasoDeTeste> findByAtividadeIdAndTipo(UUID atividadeId, TipoCasoDeTeste tipo);

    List<CasoDeTeste> findByAtividadeIdAndGrupoId(UUID atividadeId, UUID grupoId);
}
