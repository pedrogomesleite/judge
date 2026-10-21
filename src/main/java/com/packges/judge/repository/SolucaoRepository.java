package com.packges.judge.repository;

import com.packges.judge.entity.Solucao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface SolucaoRepository extends JpaRepository<Solucao, UUID> {

    List<Solucao> findByAutorIdOrderByEnviadoEmDesc(UUID autorId);

    List<Solucao> findByGrupoIdOrderByEnviadoEmDesc(UUID grupoId);

    List<Solucao> findByGrupoIdAndAutorIdOrderByEnviadoEmDesc(UUID grupoId, UUID autorId);

    /** Tentativas e envios aceitos por autor e atividade no grupo, sem carregar o código das soluções. */
    @Query("""
            select s.autor.id as autorId, s.atividade.id as atividadeId,
                   count(s) as tentativas,
                   sum(case when s.veredito = com.packges.judge.enums.Veredito.ACEITA then 1 else 0 end) as aceitas
            from Solucao s
            where s.grupo.id = :grupoId
            group by s.autor.id, s.atividade.id
            """)
    List<ResumoEnvios> resumirPorAutorEAtividade(UUID grupoId);
}
