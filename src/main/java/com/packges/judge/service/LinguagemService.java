package com.packges.judge.service;

import com.packges.judge.dto.linguagem.LinguagemResponse;
import com.packges.judge.repository.LinguagemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LinguagemService {

    private final LinguagemRepository linguagemRepository;

    @Transactional(readOnly = true)
    public List<LinguagemResponse> listarAtivas() {
        return linguagemRepository.findByAtivaTrue().stream()
                .map(LinguagemResponse::from)
                .toList();
    }
}
