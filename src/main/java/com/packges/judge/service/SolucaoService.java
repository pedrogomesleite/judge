package com.packges.judge.service;

import com.packges.judge.dto.solucao.PedidoExecucao;
import com.packges.judge.dto.solucao.ResultadoExecucao;
import com.packges.judge.dto.solucao.SolucaoCodigoResponse;
import com.packges.judge.dto.solucao.SolucaoRequest;
import com.packges.judge.dto.solucao.SolucaoResponse;
import com.packges.judge.entity.Linguagem;
import com.packges.judge.entity.MembroGrupo;
import com.packges.judge.entity.ResultadoCasoDeTeste;
import com.packges.judge.entity.Solucao;
import com.packges.judge.enums.PapelGrupo;
import com.packges.judge.enums.StatusGrupo;
import com.packges.judge.enums.StatusSolucao;
import com.packges.judge.enums.TipoAlteracaoGrupo;
import com.packges.judge.enums.TipoCasoDeTeste;
import com.packges.judge.enums.Veredito;
import com.packges.judge.exception.AcessoNegadoException;
import com.packges.judge.exception.RecursoNaoEncontradoException;
import com.packges.judge.exception.RegraNegocioException;
import com.packges.judge.messaging.GrupoAlteradoEvent;
import com.packges.judge.messaging.PublicacaoPendente;
import com.packges.judge.messaging.SolucaoAlteradaEvent;
import com.packges.judge.messaging.SolucaoEnviadaEvent;
import com.packges.judge.repository.CasoDeTesteRepository;
import com.packges.judge.repository.LinguagemRepository;
import com.packges.judge.repository.ResultadoCasoDeTesteRepository;
import com.packges.judge.repository.SolucaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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

        // Publicada na fila da linguagem depois do commit (PublicadorSolucoes, ADR-007)
        eventPublisher.publishEvent(new SolucaoEnviadaEvent(solucao.getId()));
        notificar(solucao);

        return SolucaoResponse.from(solucao);
    }

    @Transactional(readOnly = true)
    public SolucaoResponse buscar(UUID id, UUID usuarioId) {
        return SolucaoResponse.from(obterVisivel(id, usuarioId));
    }

    @Transactional(readOnly = true)
    public SolucaoCodigoResponse buscarCodigo(UUID id, UUID usuarioId) {
        return SolucaoCodigoResponse.from(obterVisivel(id, usuarioId));
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
     * Pedido de execução de uma solução ainda não publicada (PENDENTE e sem publicadaEm), com a fila da linguagem.
     * Vazio se a solução não existir ou se já tiver sido publicada ou processada.
     */
    @Transactional(readOnly = true)
    public Optional<PublicacaoPendente> prepararPublicacao(UUID solucaoId) {
        return solucaoRepository.findById(solucaoId)
                .filter(solucao -> solucao.getStatus() == StatusSolucao.PENDENTE && solucao.getPublicadaEm() == null)
                .map(solucao -> new PublicacaoPendente(solucao.getLinguagem().getFila(), new PedidoExecucao(
                        solucao.getId(),
                        solucao.getAtividade().getId(),
                        solucao.getLinguagem().getNome(),
                        solucao.getCodigo(),
                        solucao.getAtividade().getLimiteTempoMs(),
                        solucao.getAtividade().getLimiteMemoriaMb(),
                        casoDeTesteRepository.findIdsByAtividadeIdAndTipo(solucao.getAtividade().getId(), TipoCasoDeTeste.OFICIAL)
                )));
    }

    /** Grava publicadaEm depois que o broker confirma a publicação (ADR-007). */
    @Transactional
    public void marcarPublicada(UUID solucaoId) {
        solucaoRepository.marcarPublicada(solucaoId, Instant.now());
    }

    /** Ids das soluções PENDENTE sem publicadaEm enviadas antes do instante informado, até o limite. */
    @Transactional(readOnly = true)
    public List<UUID> listarNaoPublicadas(Instant enviadaAntesDe, int limite) {
        return solucaoRepository.findIdsNaoPublicadas(enviadaAntesDe, PageRequest.of(0, limite));
    }

    /**
     * Marca a solução como EM_EXECUCAO quando o runner avisa que começou (EXECUCAO_INICIADA). Só altera soluções
     * PENDENTE, então é idempotente e não desfaz um resultado que tenha chegado antes do aviso.
     */
    @Transactional
    public void marcarEmExecucao(UUID solucaoId) {
        if (solucaoRepository.marcarEmExecucao(solucaoId) > 0) {
            solucaoRepository.findById(solucaoId).ifPresent(this::notificar);
        }
    }

    /**
     * Registra o resultado produzido pelo runner (EXECUCAO_CONCLUIDA). É idempotente, porque o RabbitMQ entrega
     * at-least-once, e trava a linha para que entregas repetidas não gravem os casos duas vezes.
     */
    @Transactional
    public void registrarResultado(ResultadoExecucao resultado) {
        Optional<Solucao> encontrada = solucaoRepository.findByIdParaAtualizar(resultado.solucaoId());
        if (encontrada.isEmpty()) {
            log.warn("Resultado ignorado: solução {} não encontrada", resultado.solucaoId());
            return;
        }
        Solucao solucao = encontrada.get();
        if (finalizada(solucao)) {
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
        notificar(solucao);
    }

    /**
     * Marca a solução como FALHA (ERRO_INTERNO) quando a mensagem dela foi para a DLQ: o runner não conseguiu
     * executá-la ou o resultado não pôde ser gravado. Ignora soluções já finalizadas.
     */
    @Transactional
    public void registrarFalha(UUID solucaoId) {
        solucaoRepository.findByIdParaAtualizar(solucaoId)
                .filter(solucao -> !finalizada(solucao))
                .ifPresentOrElse(solucao -> {
                    solucao.setStatus(StatusSolucao.FALHA);
                    solucao.setVeredito(Veredito.ERRO_INTERNO);
                    log.warn("Solução {} marcada como FALHA", solucaoId);
                    notificar(solucao);
                }, () -> log.warn("Falha ignorada: solução {} não encontrada ou já finalizada", solucaoId));
    }

    private static boolean finalizada(Solucao solucao) {
        return solucao.getStatus() == StatusSolucao.CONCLUIDA || solucao.getStatus() == StatusSolucao.FALHA;
    }

    /**
     * Avisa quem acompanha a solução (SSE por solução) e, nas soluções de grupo, as listas de envios e o placar do grupo
     * (SSE do grupo). Os dois avisos saem depois do commit (ADR-003).
     */
    private void notificar(Solucao solucao) {
        eventPublisher.publishEvent(new SolucaoAlteradaEvent(SolucaoResponse.from(solucao)));
        if (solucao.getGrupo() != null) {
            eventPublisher.publishEvent(new GrupoAlteradoEvent(solucao.getGrupo().getId(), TipoAlteracaoGrupo.SOLUCOES));
        }
    }

    /**
     * Visível para o autor e para os avaliadores do grupo em que foi enviada.
     */
    private Solucao obterVisivel(UUID id, UUID usuarioId) {
        Solucao solucao = solucaoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Solução", id));

        boolean autor = solucao.getAutor().getId().equals(usuarioId);
        boolean avaliador = solucao.getGrupo() != null && grupoService.isAvaliador(solucao.getGrupo().getId(), usuarioId);
        if (!autor && !avaliador) {
            throw new AcessoNegadoException("Sem permissão para visualizar esta solução");
        }
        return solucao;
    }
}
