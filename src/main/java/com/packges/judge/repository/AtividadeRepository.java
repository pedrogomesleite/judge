package com.packges.judge.repository;

import com.packges.judge.entity.Atividade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AtividadeRepository extends JpaRepository<Atividade, UUID> {
}
