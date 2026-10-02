package com.packges.judge.repository;

import com.packges.judge.entity.GrupoAtividade;
import com.packges.judge.enums.StatusGrupo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GrupoAtividadeRepository extends JpaRepository<GrupoAtividade, UUID> {

    Optional<GrupoAtividade> findByCodigoConvite(String codigoConvite);

    List<GrupoAtividade> findByStatusAndDataInicioLessThanEqual(StatusGrupo status, Instant instante);

    List<GrupoAtividade> findByStatusAndDataFimLessThanEqual(StatusGrupo status, Instant instante);
}
