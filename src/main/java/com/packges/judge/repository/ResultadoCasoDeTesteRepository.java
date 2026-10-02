package com.packges.judge.repository;

import com.packges.judge.entity.ResultadoCasoDeTeste;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ResultadoCasoDeTesteRepository extends JpaRepository<ResultadoCasoDeTeste, UUID> {

    List<ResultadoCasoDeTeste> findBySolucaoId(UUID solucaoId);
}
