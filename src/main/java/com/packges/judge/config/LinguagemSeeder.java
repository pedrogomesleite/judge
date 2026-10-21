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
    private static final String EXTENSAO_CPP = "cpp";

    private final LinguagemRepository linguagemRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        linguagemRepository.findByNome(NOME_CPP).ifPresentOrElse(this::completarExtensao, this::cadastrarCpp);
    }

    private void cadastrarCpp() {
        Linguagem cpp = new Linguagem();
        cpp.setNome(NOME_CPP);
        cpp.setVersao("g++ (C++17)");
        cpp.setFila("submissions.cpp");
        cpp.setExtensao(EXTENSAO_CPP);
        cpp.setAtiva(true);
        linguagemRepository.save(cpp);
        log.info("Linguagem {} cadastrada", NOME_CPP);
    }

    /** Bancos criados antes da coluna extensao: o ddl-auto=update adiciona a coluna vazia. */
    private void completarExtensao(Linguagem cpp) {
        if (cpp.getExtensao() == null) {
            cpp.setExtensao(EXTENSAO_CPP);
            log.info("Extensão da linguagem {} preenchida", NOME_CPP);
        }
    }
}
