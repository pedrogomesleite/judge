package com.packges.judge.config;

import com.packges.judge.entity.Linguagem;
import com.packges.judge.repository.LinguagemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Garante a existência da única linguagem suportada por enquanto: C++ (runner-cpp).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LinguagemSeeder implements ApplicationRunner {

    private static final String NOME_CPP = "C++";

    private final LinguagemRepository linguagemRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (linguagemRepository.findByNome(NOME_CPP).isPresent()) {
            return;
        }

        Linguagem cpp = new Linguagem();
        cpp.setNome(NOME_CPP);
        cpp.setVersao("g++ (C++17)");
        cpp.setFila("submissions.cpp");
        cpp.setAtiva(true);
        linguagemRepository.save(cpp);
        log.info("Linguagem {} cadastrada", NOME_CPP);
    }
}
