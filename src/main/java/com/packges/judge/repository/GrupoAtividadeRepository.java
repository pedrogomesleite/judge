package com.packges.judge.repository;

import com.packges.judge.entity.GrupoAtividade;
import com.packges.judge.enums.StatusGrupo;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GrupoAtividadeRepository extends JpaRepository<GrupoAtividade, UUID> {

    /** Trava a linha: a entrada pelo convite espera uma exclusão em andamento e não encontra o grupo excluído. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GrupoAtividade g where g.codigoConvite = :codigoConvite")
    Optional<GrupoAtividade> findByCodigoConviteParaAtualizar(String codigoConvite);

    List<GrupoAtividade> findByStatusAndDataInicioLessThanEqual(StatusGrupo status, Instant instante);

    List<GrupoAtividade> findByStatusAndDataFimLessThanEqual(StatusGrupo status, Instant instante);

    /**
     * Trava a linha para a exclusão do grupo. A query é nativa porque, no PostgreSQL, o PESSIMISTIC_WRITE do Hibernate
     * vira FOR NO KEY UPDATE, que não bloqueia inserções com chave estrangeira para o grupo (membros, soluções, casos).
     */
    @Query(value = "select * from grupo_atividade where id = :id for update", nativeQuery = true)
    Optional<GrupoAtividade> findByIdParaExcluir(UUID id);
}
