package com.packges.judge.service;

import com.packges.judge.dto.grupo.DesempenhoAtividadeResponse;
import com.packges.judge.dto.grupo.DesempenhoEstudanteResponse;
import com.packges.judge.dto.grupo.ProgressoMembroResponse;
import com.packges.judge.entity.GrupoAtividadeItem;
import com.packges.judge.entity.MembroGrupo;
import com.packges.judge.enums.PapelGrupo;
import com.packges.judge.repository.GrupoAtividadeItemRepository;
import com.packges.judge.repository.MembroGrupoRepository;
import com.packges.judge.repository.ResumoEnvios;
import com.packges.judge.repository.SolucaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Placar e consolidação dos envios dos estudantes de um grupo.
 * <p>
 * Regras: tentativas são todos os envios do estudante na atividade; a atividade conta como aceita se houver pelo
 * menos um envio ACEITA (aceites repetidos não somam) e, aceita, vale o peso dela. Entram só os estudantes atuais
 * e as atividades que ainda fazem parte do grupo.
 */
@Service
@RequiredArgsConstructor
public class DesempenhoGrupoService {

    private final GrupoService grupoService;
    private final MembroGrupoRepository membroRepository;
    private final GrupoAtividadeItemRepository itemRepository;
    private final SolucaoRepository solucaoRepository;

    /** Para qualquer membro: atividades aceitas de cada estudante sobre o total de atividades do grupo. */
    @Transactional(readOnly = true)
    public List<ProgressoMembroResponse> progresso(UUID grupoId, UUID usuarioId) {
        grupoService.exigirMembro(grupoId, usuarioId);
        List<GrupoAtividadeItem> itens = itemRepository.findByGrupoIdOrderByOrdem(grupoId);
        return calcular(grupoId, itens, estudantes(grupoId)).stream()
                .map(desempenho -> new ProgressoMembroResponse(
                        desempenho.usuarioId(), desempenho.nome(), desempenho.aceitas(), itens.size()))
                .sorted(Comparator.comparingInt(ProgressoMembroResponse::aceitas).reversed()
                        .thenComparing(ProgressoMembroResponse::nome, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Avaliador: todos os estudantes. Estudante: só o próprio desempenho. */
    @Transactional(readOnly = true)
    public List<DesempenhoEstudanteResponse> relatorio(UUID grupoId, UUID usuarioId) {
        MembroGrupo membro = grupoService.exigirMembro(grupoId, usuarioId);
        List<MembroGrupo> alvos = membro.getPapel() == PapelGrupo.AVALIADOR ? estudantes(grupoId) : List.of(membro);
        return calcular(grupoId, itemRepository.findByGrupoIdOrderByOrdem(grupoId), alvos).stream()
                .sorted(Comparator.comparing(DesempenhoEstudanteResponse::nome, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<MembroGrupo> estudantes(UUID grupoId) {
        return membroRepository.findByGrupoId(grupoId).stream()
                .filter(membro -> membro.getPapel() == PapelGrupo.ESTUDANTE)
                .toList();
    }

    private List<DesempenhoEstudanteResponse> calcular(UUID grupoId, List<GrupoAtividadeItem> itens, List<MembroGrupo> membros) {
        Map<UUID, Map<UUID, ResumoEnvios>> porAutor = solucaoRepository.resumirPorAutorEAtividade(grupoId).stream()
                .collect(Collectors.groupingBy(ResumoEnvios::getAutorId,
                        Collectors.toMap(ResumoEnvios::getAtividadeId, resumo -> resumo)));

        return membros.stream()
                .map(membro -> {
                    Map<UUID, ResumoEnvios> doAutor = porAutor.getOrDefault(membro.getUsuario().getId(), Map.of());
                    List<DesempenhoAtividadeResponse> atividades = itens.stream()
                            .map(item -> desempenho(item, doAutor.get(item.getAtividade().getId())))
                            .toList();
                    return new DesempenhoEstudanteResponse(
                            membro.getUsuario().getId(),
                            membro.getUsuario().getNome(),
                            (int) atividades.stream().filter(DesempenhoAtividadeResponse::aceita).count(),
                            atividades.stream().mapToInt(DesempenhoAtividadeResponse::pontos).sum(),
                            atividades);
                })
                .toList();
    }

    private DesempenhoAtividadeResponse desempenho(GrupoAtividadeItem item, ResumoEnvios resumo) {
        long tentativas = resumo != null ? resumo.getTentativas() : 0;
        boolean aceita = resumo != null && resumo.getAceitas() > 0;
        return new DesempenhoAtividadeResponse(
                item.getAtividade().getId(), item.getPeso(), tentativas, aceita, aceita ? item.getPeso() : 0);
    }
}
