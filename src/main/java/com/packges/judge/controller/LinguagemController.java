package com.packges.judge.controller;

import com.packges.judge.dto.linguagem.LinguagemResponse;
import com.packges.judge.service.LinguagemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/languages")
@RequiredArgsConstructor
public class LinguagemController {

    private final LinguagemService linguagemService;

    @GetMapping
    public List<LinguagemResponse> listar() {
        return linguagemService.listarAtivas();
    }
}
