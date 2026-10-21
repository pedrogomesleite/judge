package com.packges.judge.service;

import com.packges.judge.dto.grupo.GrupoAtividadeItemResponse;
import com.packges.judge.dto.grupo.GrupoAtividadeRequest;
import com.packges.judge.dto.grupo.GrupoRequest;
import com.packges.judge.dto.grupo.GrupoResponse;
import com.packges.judge.dto.grupo.MembroResponse;
import com.packges.judge.entity.Atividade;
import com.packges.judge.entity.GrupoAtividade;
import com.packges.judge.entity.GrupoAtividadeItem;
import com.packges.judge.entity.MembroGrupo;
import com.packges.judge.entity.Usuario;
import com.packges.judge.enums.PapelGrupo;
import com.packges.judge.enums.StatusGrupo;
import com.packges.judge.exception.AcessoNegadoException;
import com.packges.judge.exception.ConflitoException;
import com.packges.judge.exception.RecursoNaoEncontradoException;
import com.packges.judge.exception.RegraNegocioException;
import com.packges.judge.repository.AtividadeRepository;
import com.packges.judge.repository.GrupoAtividadeItemRepository;
import com.packges.judge.repository.GrupoAtividadeRepository;
import com.packges.judge.repository.MembroGrupoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GrupoService {

    private static final int TAMANHO_CODIGO_CONVITE_BYTES = 16;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final GrupoAtividadeRepository grupoRepository;
    private final MembroGrupoRepository membroRepository;
    private final GrupoAtividadeItemRepository itemRepository;
    private final AtividadeRepository atividadeRepository;
    private final UsuarioService usuarioService;

    @Transactional
    public GrupoResponse criar(GrupoRequest request, UUID usuarioId) {
        validarDatas(request.dataInicio(), request.dataFim());
        Usuario criador = usuarioService.obterEntidade(usuarioId);

        GrupoAtividade grupo = new GrupoAtividade();
        grupo.setCriador(criador);
        grupo.setNome(request.nome());
        grupo.setDescricao(request.descricao());
        grupo.setDataInicio(request.dataInicio());
        grupo.setDataFim(request.dataFim());
        grupo.setStatus(StatusGrupo.AGUARDANDO_INICIO);
        grupo.setCodigoConvite(gerarCodigoConvite());
        grupoRepository.save(grupo);

        adicionarMembro(grupo, criador, PapelGrupo.AVALIADOR);

        log.info("Grupo {} criado pelo usuário {}", grupo.getId(), usuarioId);
        return GrupoResponse.from(grupo, PapelGrupo.AVALIADOR);
    }

    @Transactional(readOnly = true)
    public List<GrupoResponse> listarMeus(UUID usuarioId) {
        return membroRepository.findByUsuarioId(usuarioId).stream()
                .map(membro -> GrupoResponse.from(membro.getGrupo(), membro.getPapel()))
                .toList();
    }

    @Transactional(readOnly = true)
    public GrupoResponse buscar(UUID grupoId, UUID usuarioId) {
        MembroGrupo membro = exigirMembro(grupoId, usuarioId);
        return GrupoResponse.from(membro.getGrupo(), membro.getPapel());
    }

    @Transactional
    public GrupoResponse editar(UUID grupoId, GrupoRequest request, UUID usuarioId) {
        MembroGrupo membro = exigirAvaliador(grupoId, usuarioId);
        GrupoAtividade grupo = membro.getGrupo();
        exigirNaoFinalizado(grupo);
        validarDatas(request.dataInicio(), request.dataFim());

        grupo.setNome(request.nome());
        grupo.setDescricao(request.descricao());
        grupo.setDataInicio(request.dataInicio());
        grupo.setDataFim(request.dataFim());
        return GrupoResponse.from(grupo, membro.getPapel());
    }

    @Transactional(readOnly = true)
    public List<GrupoAtividadeItemResponse> listarAtividades(UUID grupoId, UUID usuarioId) {
        exigirMembro(grupoId, usuarioId);
        return itemRepository.findByGrupoIdOrderByOrdem(grupoId).stream()
                .map(GrupoAtividadeItemResponse::from)
                .toList();
    }

    @Transactional
    public GrupoAtividadeItemResponse adicionarAtividade(UUID grupoId, GrupoAtividadeRequest request, UUID usuarioId) {
        GrupoAtividade grupo = exigirAvaliador(grupoId, usuarioId).getGrupo();
        exigirNaoFinalizado(grupo);

        Atividade atividade = atividadeRepository.findById(request.atividadeId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Atividade", request.atividadeId()));
        if (itemRepository.existsByGrupoIdAndAtividadeId(grupoId, atividade.getId())) {
            throw new ConflitoException("Atividade já faz parte do grupo");
        }

        GrupoAtividadeItem item = new GrupoAtividadeItem();
        item.setGrupo(grupo);
        item.setAtividade(atividade);
        item.setOrdem(request.ordem());
        itemRepository.save(item);
        return GrupoAtividadeItemResponse.from(item);
    }

    @Transactional
    public void removerAtividade(UUID grupoId, UUID atividadeId, UUID usuarioId) {
        GrupoAtividade grupo = exigirAvaliador(grupoId, usuarioId).getGrupo();
        exigirNaoFinalizado(grupo);

        if (itemRepository.deleteByGrupoIdAndAtividadeId(grupoId, atividadeId) == 0) {
            throw new RecursoNaoEncontradoException("Atividade no grupo", atividadeId);
        }
    }

    @Transactional
    public GrupoResponse iniciar(UUID grupoId, UUID usuarioId) {
        MembroGrupo membro = exigirAvaliador(grupoId, usuarioId);
        transicionar(membro.getGrupo(), StatusGrupo.AGUARDANDO_INICIO, StatusGrupo.EM_ANDAMENTO);
        return GrupoResponse.from(membro.getGrupo(), membro.getPapel());
    }

    @Transactional
    public GrupoResponse finalizar(UUID grupoId, UUID usuarioId) {
        MembroGrupo membro = exigirAvaliador(grupoId, usuarioId);
        transicionar(membro.getGrupo(), StatusGrupo.EM_ANDAMENTO, StatusGrupo.FINALIZADO);
        return GrupoResponse.from(membro.getGrupo(), membro.getPapel());
    }

    @Transactional(readOnly = true)
    public List<MembroResponse> listarMembros(UUID grupoId, UUID usuarioId) {
        exigirMembro(grupoId, usuarioId);
        return membroRepository.findByGrupoId(grupoId).stream()
                .map(MembroResponse::from)
                .toList();
    }

    @Transactional
    public MembroResponse promover(UUID grupoId, UUID membroUsuarioId, UUID usuarioId) {
        exigirAvaliador(grupoId, usuarioId);
        MembroGrupo alvo = membroRepository.findByGrupoIdAndUsuarioId(grupoId, membroUsuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Membro do grupo", membroUsuarioId));

        alvo.setPapel(PapelGrupo.AVALIADOR);
        return MembroResponse.from(alvo);
    }

    /**
     * Entrada pelo link de convite. É idempotente: quem já é membro apenas recebe o grupo.
     */
    @Transactional
    public GrupoResponse entrarPorConvite(String codigoConvite, UUID usuarioId) {
        GrupoAtividade grupo = grupoRepository.findByCodigoConvite(codigoConvite)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Convite", codigoConvite));

        var membroExistente = membroRepository.findByGrupoIdAndUsuarioId(grupo.getId(), usuarioId);
        if (membroExistente.isPresent()) {
            return GrupoResponse.from(grupo, membroExistente.get().getPapel());
        }
        if (grupo.getStatus() == StatusGrupo.FINALIZADO) {
            throw new RegraNegocioException("Não é possível entrar em um grupo finalizado");
        }

        adicionarMembro(grupo, usuarioService.obterEntidade(usuarioId), PapelGrupo.ESTUDANTE);
        log.info("Usuário {} entrou no grupo {} por convite", usuarioId, grupo.getId());
        return GrupoResponse.from(grupo, PapelGrupo.ESTUDANTE);
    }

    /**
     * Avança o status dos grupos cujas datas de início/fim já foram atingidas.
     */
    @Transactional
    public void atualizarStatusPorData(Instant agora) {
        for (GrupoAtividade grupo : grupoRepository.findByStatusAndDataInicioLessThanEqual(StatusGrupo.AGUARDANDO_INICIO, agora)) {
            grupo.setStatus(StatusGrupo.EM_ANDAMENTO);
            log.info("Grupo {} iniciado automaticamente", grupo.getId());
        }
        for (GrupoAtividade grupo : grupoRepository.findByStatusAndDataFimLessThanEqual(StatusGrupo.EM_ANDAMENTO, agora)) {
            grupo.setStatus(StatusGrupo.FINALIZADO);
            log.info("Grupo {} finalizado automaticamente", grupo.getId());
        }
    }

    MembroGrupo exigirMembro(UUID grupoId, UUID usuarioId) {
        if (!grupoRepository.existsById(grupoId)) {
            throw new RecursoNaoEncontradoException("Grupo", grupoId);
        }
        return membroRepository.findByGrupoIdAndUsuarioId(grupoId, usuarioId)
                .orElseThrow(() -> new AcessoNegadoException("Usuário não é membro do grupo"));
    }

    MembroGrupo exigirAvaliador(UUID grupoId, UUID usuarioId) {
        MembroGrupo membro = exigirMembro(grupoId, usuarioId);
        if (membro.getPapel() != PapelGrupo.AVALIADOR) {
            throw new AcessoNegadoException("Ação permitida apenas para avaliadores do grupo");
        }
        return membro;
    }

    boolean isAvaliador(UUID grupoId, UUID usuarioId) {
        return membroRepository.findByGrupoIdAndUsuarioId(grupoId, usuarioId)
                .map(membro -> membro.getPapel() == PapelGrupo.AVALIADOR)
                .orElse(false);
    }

    List<UUID> idsDosGrupos(UUID usuarioId) {
        return membroRepository.findByUsuarioId(usuarioId).stream()
                .map(membro -> membro.getGrupo().getId())
                .toList();
    }

    boolean contemAtividade(UUID grupoId, UUID atividadeId) {
        return itemRepository.existsByGrupoIdAndAtividadeId(grupoId, atividadeId);
    }

    private void adicionarMembro(GrupoAtividade grupo, Usuario usuario, PapelGrupo papel) {
        MembroGrupo membro = new MembroGrupo();
        membro.setGrupo(grupo);
        membro.setUsuario(usuario);
        membro.setPapel(papel);
        membroRepository.save(membro);
    }

    private void transicionar(GrupoAtividade grupo, StatusGrupo esperado, StatusGrupo novo) {
        if (grupo.getStatus() != esperado) {
            throw new RegraNegocioException("Transição inválida: grupo está %s".formatted(grupo.getStatus()));
        }
        grupo.setStatus(novo);
        log.info("Grupo {} alterado manualmente para {}", grupo.getId(), novo);
    }

    private void exigirNaoFinalizado(GrupoAtividade grupo) {
        if (grupo.getStatus() == StatusGrupo.FINALIZADO) {
            throw new RegraNegocioException("Grupo finalizado não pode ser alterado");
        }
    }

    private void validarDatas(Instant dataInicio, Instant dataFim) {
        if (dataInicio != null && dataFim != null && !dataFim.isAfter(dataInicio)) {
            throw new RegraNegocioException("dataFim deve ser posterior a dataInicio");
        }
    }

    private String gerarCodigoConvite() {
        byte[] bytes = new byte[TAMANHO_CODIGO_CONVITE_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
