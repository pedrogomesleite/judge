package com.packges.judge.repository;

import com.packges.judge.entity.ResultadoCasoDeTeste;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ResultadoCasoDeTesteRepository extends JpaRepository<ResultadoCasoDeTeste, UUID> {

    List<ResultadoCasoDeTeste> findBySolucaoId(UUID solucaoId);

    /** Resultados das soluções da atividade (exclusão da atividade). */
    @Modifying
    @Query("""
            delete from ResultadoCasoDeTeste r
            where r.solucao.id in (select s.id from Solucao s where s.atividade.id = :atividadeId)
            """)
    int deleteByAtividadeId(UUID atividadeId);

    /** Resultados das soluções enviadas no grupo (exclusão do grupo). */
    @Modifying
    @Query("""
            delete from ResultadoCasoDeTeste r
            where r.solucao.id in (select s.id from Solucao s where s.grupo.id = :grupoId)
            """)
    int deleteByGrupoId(UUID grupoId);
}
