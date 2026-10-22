package com.packges.judge.repository;

import com.packges.judge.entity.Solucao;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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

    /** Trava a linha (SELECT ... FOR UPDATE): resultados e falhas da mesma solução são gravados um de cada vez. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Solucao s where s.id = :id")
    Optional<Solucao> findByIdParaAtualizar(UUID id);

    /** Só PENDENTE → EM_EXECUCAO. Altera só o status, sem sobrescrever um resultado gravado em paralelo. */
    @Modifying(clearAutomatically = true)
    @Query("""
            update Solucao s set s.status = com.packges.judge.enums.StatusSolucao.EM_EXECUCAO
            where s.id = :id and s.status = com.packges.judge.enums.StatusSolucao.PENDENTE
            """)
    int marcarEmExecucao(UUID id);

    /** Altera só publicadaEm, sem sobrescrever o status gravado em paralelo pelo resultado do runner. */
    @Modifying
    @Query("update Solucao s set s.publicadaEm = :publicadaEm where s.id = :id and s.publicadaEm is null")
    int marcarPublicada(UUID id, Instant publicadaEm);

    /** Soluções PENDENTE ainda não publicadas no broker, das mais antigas para as mais novas (ADR-007). */
    @Query("""
            select s.id from Solucao s
            where s.status = com.packges.judge.enums.StatusSolucao.PENDENTE
              and s.publicadaEm is null
              and s.enviadoEm < :enviadaAntesDe
            order by s.enviadoEm
            """)
    List<UUID> findIdsNaoPublicadas(Instant enviadaAntesDe, Pageable pagina);
}
