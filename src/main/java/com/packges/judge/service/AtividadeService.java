package com.packges.judge.service;

import com.packges.judge.dto.atividade.AtividadeRequest;
import com.packges.judge.dto.atividade.AtividadeResponse;
import com.packges.judge.dto.atividade.CasoDeTesteExecucaoResponse;
import com.packges.judge.dto.atividade.CasoDeTesteRequest;
import com.packges.judge.dto.atividade.CasoDeTesteResponse;
import com.packges.judge.dto.atividade.PermissoesAtividadeResponse;
import com.packges.judge.entity.Atividade;
import com.packges.judge.entity.CasoDeTeste;
import com.packges.judge.entity.MembroGrupo;
import com.packges.judge.enums.TipoCasoDeTeste;
import com.packges.judge.exception.AcessoNegadoException;
import com.packges.judge.exception.RecursoNaoEncontradoException;
import com.packges.judge.exception.RegraNegocioException;
import com.packges.judge.repository.AtividadeRepository;
import com.packges.judge.repository.CasoDeTesteRepository;
import com.packges.judge.repository.ResultadoCasoDeTesteRepository;
import com.packges.judge.repository.SolucaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AtividadeService {

    private final AtividadeRepository atividadeRepository;
    private final CasoDeTesteRepository casoDeTesteRepository;
    private final SolucaoRepository solucaoRepository;
    private final ResultadoCasoDeTesteRepository resultadoRepository;
    private final UsuarioService usuarioService;
    private final GrupoService grupoService;

    @Transactional
    public AtividadeResponse criar(AtividadeRequest request, UUID usuarioId) {
        Atividade atividade = new Atividade();
        atividade.setAutor(usuarioService.obterEntidade(usuarioId));
        atividade.setCriadoEm(Instant.now());
        aplicar(atividade, request);
        atividadeRepository.save(atividade);

        log.info("Atividade {} criada pelo usuário {}", atividade.getId(), usuarioId);
        return AtividadeResponse.from(atividade);
    }

    @Transactional(readOnly = true)
    public List<AtividadeResponse> listar() {
        return atividadeRepository.findAll(Sort.by(Sort.Direction.DESC, "criadoEm")).stream()
                .map(AtividadeResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AtividadeResponse buscar(UUID id) {
        return AtividadeResponse.from(obterEntidade(id));
    }

    /**
     * Só o autor, sendo AVALIADOR em algum grupo, e só se a atividade não estiver em um grupo já iniciado.
     */
    @Transactional
    public AtividadeResponse editar(UUID id, AtividadeRequest request, UUID usuarioId) {
        Atividade atividade = obterEntidade(id);
        impedimentoEdicao(atividade, usuarioId).ifPresent(e -> {
            throw e;
        });
        aplicar(atividade, request);

        log.info("Atividade {} editada pelo usuário {}", id, usuarioId);
        return AtividadeResponse.from(atividade);
    }

    /**
     * Só o autor, sendo AVALIADOR em algum grupo, e só se a atividade não fizer parte de nenhum grupo.
     * Apaga junto os casos de teste, as soluções avulsas e os resultados delas.
     */
    @Transactional
    public void excluir(UUID id, UUID usuarioId) {
        Atividade atividade = obterEntidade(id);
        impedimentoExclusao(atividade, usuarioId).ifPresent(e -> {
            throw e;
        });

        int resultados = resultadoRepository.deleteByAtividadeId(id);
        int solucoes = solucaoRepository.deleteByAtividadeId(id);
        int casos = casoDeTesteRepository.deleteByAtividadeId(id);
        atividadeRepository.delete(atividade);

        log.info("Atividade {} excluída pelo usuário {} ({} casos de teste, {} soluções, {} resultados)",
                id, usuarioId, casos, solucoes, resultados);
    }

    @Transactional(readOnly = true)
    public PermissoesAtividadeResponse permissoes(UUID id, UUID usuarioId) {
        Atividade atividade = obterEntidade(id);
        Optional<RuntimeException> edicao = impedimentoEdicao(atividade, usuarioId);
        Optional<RuntimeException> exclusao = impedimentoExclusao(atividade, usuarioId);
        return new PermissoesAtividadeResponse(
                edicao.isEmpty(), edicao.map(RuntimeException::getMessage).orElse(null),
                exclusao.isEmpty(), exclusao.map(RuntimeException::getMessage).orElse(null));
    }

    /**
     * OFICIAL: apenas o autor da atividade cria; são os casos usados no veredito. Pode ser público (exemplo).
     * COMUNITARIO: criado por membros de um grupo que contém a atividade; visível só para o grupo.
     */
    @Transactional
    public CasoDeTesteResponse criarCasoDeTeste(UUID atividadeId, CasoDeTesteRequest request, UUID usuarioId) {
        Atividade atividade = obterEntidade(atividadeId);

        CasoDeTeste caso = new CasoDeTeste();
        caso.setAtividade(atividade);
        caso.setAutor(usuarioService.obterEntidade(usuarioId));
        caso.setEntrada(request.entrada());
        caso.setSaidaEsperada(request.saidaEsperada());
        caso.setTipo(request.tipo());

        if (request.tipo() == TipoCasoDeTeste.OFICIAL) {
            exigirAutor(atividade, usuarioId);
            caso.setPublico(Boolean.TRUE.equals(request.publico()));
        } else {
            if (Boolean.TRUE.equals(request.publico())) {
                throw new RegraNegocioException("Casos de teste comunitários são visíveis só para o grupo e não podem ser públicos");
            }
            if (request.grupoId() == null) {
                throw new RegraNegocioException("grupoId é obrigatório para casos de teste comunitários");
            }
            MembroGrupo membro = grupoService.exigirMembro(request.grupoId(), usuarioId);
            if (!grupoService.contemAtividade(request.grupoId(), atividadeId)) {
                throw new RegraNegocioException("Atividade não faz parte do grupo");
            }
            caso.setGrupo(membro.getGrupo());
        }

        casoDeTesteRepository.save(caso);
        return CasoDeTesteResponse.from(caso);
    }

    /**
     * Sem grupoId: casos oficiais, visíveis apenas para o autor da atividade.
     * Com grupoId: casos comunitários do grupo, visíveis para os membros.
     */
    @Transactional(readOnly = true)
    public List<CasoDeTesteResponse> listarCasosDeTeste(UUID atividadeId, UUID grupoId, UUID usuarioId) {
        Atividade atividade = obterEntidade(atividadeId);

        List<CasoDeTeste> casos;
        if (grupoId == null) {
            exigirAutor(atividade, usuarioId);
            casos = casoDeTesteRepository.findByAtividadeIdAndTipo(atividadeId, TipoCasoDeTeste.OFICIAL);
        } else {
            grupoService.exigirMembro(grupoId, usuarioId);
            casos = casoDeTesteRepository.findByAtividadeIdAndGrupoId(atividadeId, grupoId);
        }
        return casos.stream().map(CasoDeTesteResponse::from).toList();
    }

    /**
     * Marca ou desmarca um caso OFICIAL como público. Apenas o autor da atividade.
     */
    @Transactional
    public CasoDeTesteResponse alterarPublico(UUID atividadeId, UUID casoId, boolean publico, UUID usuarioId) {
        exigirAutor(obterEntidade(atividadeId), usuarioId);
        CasoDeTeste caso = casoDeTesteRepository.findByIdAndAtividadeId(casoId, atividadeId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Caso de teste", casoId));
        if (caso.getTipo() != TipoCasoDeTeste.OFICIAL) {
            throw new RegraNegocioException("Casos de teste comunitários são visíveis só para o grupo e não podem ser públicos");
        }

        caso.setPublico(publico);
        log.info("Caso de teste {} da atividade {} agora é {}", casoId, atividadeId, publico ? "público" : "privado");
        return CasoDeTesteResponse.from(caso);
    }

    /**
     * Casos oficiais marcados como públicos: exemplos visíveis para qualquer pessoa, inclusive sem login.
     */
    @Transactional(readOnly = true)
    public List<CasoDeTesteResponse> listarCasosPublicos(UUID atividadeId) {
        obterEntidade(atividadeId);
        return casoDeTesteRepository.findByAtividadeIdAndTipoAndPublicoTrue(atividadeId, TipoCasoDeTeste.OFICIAL).stream()
                .map(CasoDeTesteResponse::from)
                .toList();
    }

    /**
     * Casos comunitários da atividade compartilhados em todos os grupos de que o usuário é membro.
     * Cada caso traz o grupo de origem (grupoId e grupoNome).
     */
    @Transactional(readOnly = true)
    public List<CasoDeTesteResponse> listarCasosCompartilhados(UUID atividadeId, UUID usuarioId) {
        obterEntidade(atividadeId);
        List<UUID> grupoIds = grupoService.idsDosGrupos(usuarioId);
        if (grupoIds.isEmpty()) {
            return List.of();
        }
        return casoDeTesteRepository.findByAtividadeIdAndTipoAndGrupoIdIn(atividadeId, TipoCasoDeTeste.COMUNITARIO, grupoIds).stream()
                .map(CasoDeTesteResponse::from)
                .toList();
    }

    /**
     * Casos oficiais com a entrada e a saída esperada, para o runner executar a solução (endpoint interno, ADR-008).
     */
    @Transactional(readOnly = true)
    public List<CasoDeTesteExecucaoResponse> listarCasosOficiaisParaExecucao(UUID atividadeId) {
        obterEntidade(atividadeId);
        return casoDeTesteRepository.findByAtividadeIdAndTipo(atividadeId, TipoCasoDeTeste.OFICIAL).stream()
                .map(CasoDeTesteExecucaoResponse::from)
                .toList();
    }

    Atividade obterEntidade(UUID id) {
        return atividadeRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Atividade", id));
    }

    private void exigirAutor(Atividade atividade, UUID usuarioId) {
        if (!atividade.getAutor().getId().equals(usuarioId)) {
            throw new AcessoNegadoException("Ação permitida apenas para o autor da atividade");
        }
    }

    /**
     * A exceção que impede editar, ou vazio se o usuário pode. Usada tanto para recusar a edição quanto para
     * informar as permissões ao frontend, para as duas regras não divergirem.
     */
    private Optional<RuntimeException> impedimentoEdicao(Atividade atividade, UUID usuarioId) {
        return impedimentoAutorAvaliador(atividade, usuarioId)
                .or(() -> grupoService.atividadeEmGrupoIniciado(atividade.getId())
                        ? Optional.of(new RegraNegocioException(
                                "A atividade faz parte de um grupo em andamento ou finalizado e não pode ser editada"))
                        : Optional.empty());
    }

    private Optional<RuntimeException> impedimentoExclusao(Atividade atividade, UUID usuarioId) {
        return impedimentoAutorAvaliador(atividade, usuarioId)
                .or(() -> grupoService.atividadeEmAlgumGrupo(atividade.getId())
                        ? Optional.of(new RegraNegocioException(
                                "A atividade faz parte de um grupo; remova-a dos grupos antes de excluí-la"))
                        : Optional.empty());
    }

    private Optional<RuntimeException> impedimentoAutorAvaliador(Atividade atividade, UUID usuarioId) {
        if (!atividade.getAutor().getId().equals(usuarioId)) {
            return Optional.of(new AcessoNegadoException("Apenas o autor pode editar ou excluir a atividade"));
        }
        if (!grupoService.isAvaliadorEmAlgumGrupo(usuarioId)) {
            return Optional.of(new AcessoNegadoException(
                    "Apenas avaliadores podem editar ou excluir atividades: crie um grupo ou seja promovido a avaliador"));
        }
        return Optional.empty();
    }

    private void aplicar(Atividade atividade, AtividadeRequest request) {
        atividade.setTitulo(request.titulo());
        atividade.setEnunciado(request.enunciado());
        atividade.setLimiteTempoMs(request.limiteTempoMs());
        atividade.setLimiteMemoriaMb(request.limiteMemoriaMb());
    }
}
