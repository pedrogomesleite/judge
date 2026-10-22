package com.packges.judge.service;

import com.packges.judge.dto.atividade.AtividadeRequest;
import com.packges.judge.dto.atividade.AtividadeResponse;
import com.packges.judge.dto.atividade.CasoDeTesteExecucaoResponse;
import com.packges.judge.dto.atividade.CasoDeTesteRequest;
import com.packges.judge.dto.atividade.CasoDeTesteResponse;
import com.packges.judge.entity.Atividade;
import com.packges.judge.entity.CasoDeTeste;
import com.packges.judge.entity.MembroGrupo;
import com.packges.judge.enums.TipoCasoDeTeste;
import com.packges.judge.exception.AcessoNegadoException;
import com.packges.judge.exception.RecursoNaoEncontradoException;
import com.packges.judge.exception.RegraNegocioException;
import com.packges.judge.repository.AtividadeRepository;
import com.packges.judge.repository.CasoDeTesteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AtividadeService {

    private final AtividadeRepository atividadeRepository;
    private final CasoDeTesteRepository casoDeTesteRepository;
    private final UsuarioService usuarioService;
    private final GrupoService grupoService;

    @Transactional
    public AtividadeResponse criar(AtividadeRequest request, UUID usuarioId) {
        Atividade atividade = new Atividade();
        atividade.setAutor(usuarioService.obterEntidade(usuarioId));
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

    @Transactional
    public AtividadeResponse editar(UUID id, AtividadeRequest request, UUID usuarioId) {
        Atividade atividade = obterEntidade(id);
        exigirAutor(atividade, usuarioId);
        aplicar(atividade, request);
        return AtividadeResponse.from(atividade);
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

    private void aplicar(Atividade atividade, AtividadeRequest request) {
        atividade.setTitulo(request.titulo());
        atividade.setEnunciado(request.enunciado());
        atividade.setLimiteTempoMs(request.limiteTempoMs());
        atividade.setLimiteMemoriaMb(request.limiteMemoriaMb());
        atividade.setCriadoEm(new Date().toInstant());
    }
}
