package com.packges.judge.controller;

import com.packges.judge.dto.atividade.CasoDeTesteExecucaoResponse;
import com.packges.judge.service.AtividadeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints internos usados pelos runners, autenticados pelo token de serviço e não pelo JWT (ADR-008).
 */
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
public class RunnerController {

    private final AtividadeService atividadeService;

    /** Casos OFICIAL da atividade. São imutáveis, então o runner os guarda em cache pelo id. */
    @GetMapping("/activities/{id}/test-cases")
    public List<CasoDeTesteExecucaoResponse> casosDeTeste(@PathVariable UUID id) {
        return atividadeService.listarCasosOficiaisParaExecucao(id);
    }
}
