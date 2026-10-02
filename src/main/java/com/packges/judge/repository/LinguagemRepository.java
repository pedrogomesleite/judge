package com.packges.judge.repository;

import com.packges.judge.entity.Linguagem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LinguagemRepository extends JpaRepository<Linguagem, UUID> {

    List<Linguagem> findByAtivaTrue();

    Optional<Linguagem> findByNome(String nome);
}
