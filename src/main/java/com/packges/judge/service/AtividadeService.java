package com.packges.judge.service;

import com.packges.judge.dto.atividade.AtividadeRequest;
import com.packges.judge.dto.atividade.AtividadeResponse;
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
     * OFICIAL: apenas o autor da atividade cria; são os casos usados no veredito.
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
        } else {
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
    }
}
