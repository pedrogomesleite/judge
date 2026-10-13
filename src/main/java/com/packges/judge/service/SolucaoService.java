package com.packges.judge.service;

import com.packges.judge.dto.solucao.ResultadoExecucao;
import com.packges.judge.dto.solucao.SolucaoRequest;
import com.packges.judge.dto.solucao.SolucaoResponse;
import com.packges.judge.entity.Linguagem;
import com.packges.judge.entity.MembroGrupo;
import com.packges.judge.entity.ResultadoCasoDeTeste;
import com.packges.judge.entity.Solucao;
import com.packges.judge.enums.PapelGrupo;
import com.packges.judge.enums.StatusGrupo;
import com.packges.judge.enums.StatusSolucao;
import com.packges.judge.enums.Veredito;
import com.packges.judge.exception.AcessoNegadoException;
import com.packges.judge.exception.RecursoNaoEncontradoException;
import com.packges.judge.exception.RegraNegocioException;
import com.packges.judge.messaging.SolucaoEnviadaEvent;
import com.packges.judge.repository.CasoDeTesteRepository;
import com.packges.judge.repository.LinguagemRepository;
import com.packges.judge.repository.ResultadoCasoDeTesteRepository;
import com.packges.judge.repository.SolucaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SolucaoService {

    private final SolucaoRepository solucaoRepository;
    private final ResultadoCasoDeTesteRepository resultadoRepository;
    private final CasoDeTesteRepository casoDeTesteRepository;
    private final LinguagemRepository linguagemRepository;
    private final UsuarioService usuarioService;
    private final AtividadeService atividadeService;
    private final GrupoService grupoService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public SolucaoResponse enviar(SolucaoRequest request, UUID usuarioId) {
        Linguagem linguagem = linguagemRepository.findById(request.linguagemId())
                .filter(Linguagem::isAtiva)
                .orElseThrow(() -> new RegraNegocioException("Linguagem não suportada"));

        Solucao solucao = new Solucao();
        solucao.setAutor(usuarioService.obterEntidade(usuarioId));
        solucao.setAtividade(atividadeService.obterEntidade(request.atividadeId()));
        solucao.setLinguagem(linguagem);
        solucao.setCodigo(request.codigo());
        solucao.setStatus(StatusSolucao.PENDENTE);

        if (request.grupoId() != null) {
            MembroGrupo membro = grupoService.exigirMembro(request.grupoId(), usuarioId);
            if (membro.getGrupo().getStatus() != StatusGrupo.EM_ANDAMENTO) {
                throw new RegraNegocioException("O grupo não está em andamento");
            }
            if (!grupoService.contemAtividade(request.grupoId(), request.atividadeId())) {
                throw new RegraNegocioException("Atividade não faz parte do grupo");
            }
            solucao.setGrupo(membro.getGrupo());
        }

        solucaoRepository.save(solucao);
        log.info("Solução {} recebida (atividade {}, linguagem {})", solucao.getId(), request.atividadeId(), linguagem.getNome());

        // TODO(rabbitmq): um @TransactionalEventListener(AFTER_COMMIT) publica este evento em linguagem.getFila()
        //  e grava publicadaEm; + job de reconciliação das não publicadas (ADR-006, ADR-007).
        //  Enquanto isso, o RunnerSimulado responde.
        eventPublisher.publishEvent(new SolucaoEnviadaEvent(
                solucao.getId(), request.atividadeId(), linguagem.getFila(), solucao.getCodigo()));

        return SolucaoResponse.from(solucao);
    }

    /**
     * Visível para o autor e para os avaliadores do grupo em que foi enviada.
     */
    @Transactional(readOnly = true)
    public SolucaoResponse buscar(UUID id, UUID usuarioId) {
        Solucao solucao = solucaoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Solução", id));

        boolean autor = solucao.getAutor().getId().equals(usuarioId);
        boolean avaliador = solucao.getGrupo() != null && grupoService.isAvaliador(solucao.getGrupo().getId(), usuarioId);
        if (!autor && !avaliador) {
            throw new AcessoNegadoException("Sem permissão para visualizar esta solução");
        }
        return SolucaoResponse.from(solucao);
    }

    /**
     * Sem grupoId: soluções do próprio usuário.
     * Com grupoId: todas as soluções do grupo para avaliadores; apenas as próprias para estudantes.
     */
    @Transactional(readOnly = true)
    public List<SolucaoResponse> listar(UUID grupoId, UUID usuarioId) {
        List<Solucao> solucoes;
        if (grupoId == null) {
            solucoes = solucaoRepository.findByAutorIdOrderByEnviadoEmDesc(usuarioId);
        } else if (grupoService.exigirMembro(grupoId, usuarioId).getPapel() == PapelGrupo.AVALIADOR) {
            solucoes = solucaoRepository.findByGrupoIdOrderByEnviadoEmDesc(grupoId);
        } else {
            solucoes = solucaoRepository.findByGrupoIdAndAutorIdOrderByEnviadoEmDesc(grupoId, usuarioId);
        }
        return solucoes.stream().map(SolucaoResponse::from).toList();
    }

    /**
     * Marca a solução como EM_EXECUCAO. Só altera soluções PENDENTE, então é idempotente.
     */
    // TODO(rabbitmq): chamado quando o runner sinalizar o início da execução (hoje: RunnerSimulado)
    @Transactional
    public void marcarEmExecucao(UUID solucaoId) {
        solucaoRepository.findById(solucaoId)
                .filter(solucao -> solucao.getStatus() == StatusSolucao.PENDENTE)
                .ifPresent(solucao -> solucao.setStatus(StatusSolucao.EM_EXECUCAO));
    }

    /**
     * Registra o resultado produzido pelo runner. É idempotente, porque o RabbitMQ entrega at-least-once.
     */
    // TODO(rabbitmq): chamado pelo @RabbitListener da fila submissions.results (hoje: RunnerSimulado)
    @Transactional
    public void registrarResultado(ResultadoExecucao resultado) {
        Solucao solucao = solucaoRepository.findById(resultado.solucaoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Solução", resultado.solucaoId()));

        if (solucao.getStatus() == StatusSolucao.CONCLUIDA || solucao.getStatus() == StatusSolucao.FALHA) {
            log.warn("Resultado duplicado ignorado para a solução {}", solucao.getId());
            return;
        }

        solucao.setVeredito(resultado.veredito());
        solucao.setStatus(resultado.veredito() == Veredito.ERRO_INTERNO ? StatusSolucao.FALHA : StatusSolucao.CONCLUIDA);
        solucao.setTempoMs(resultado.tempoMs());
        solucao.setMemoriaKb(resultado.memoriaKb());

        if (resultado.casos() != null) {
            List<ResultadoCasoDeTeste> resultados = resultado.casos().stream()
                    .map(caso -> {
                        ResultadoCasoDeTeste resultadoCaso = new ResultadoCasoDeTeste();
                        resultadoCaso.setSolucao(solucao);
                        resultadoCaso.setCasoDeTeste(casoDeTesteRepository.getReferenceById(caso.casoDeTesteId()));
                        resultadoCaso.setVeredito(caso.veredito());
                        resultadoCaso.setTempoMs(caso.tempoMs());
                        resultadoCaso.setMemoriaKb(caso.memoriaKb());
                        return resultadoCaso;
                    })
                    .toList();
            resultadoRepository.saveAll(resultados);
        }

        log.info("Solução {} avaliada: {}", solucao.getId(), solucao.getVeredito());
        // TODO(sse): notificar o cliente (ADR-003)
    }
}
