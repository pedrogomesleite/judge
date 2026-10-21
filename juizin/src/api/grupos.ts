import { abrirStream, request } from './http'
import type {
  DesempenhoEstudanteResponse,
  GrupoAtividadeItemResponse,
  GrupoAtividadeRequest,
  GrupoRequest,
  GrupoResponse,
  MembroResponse,
  ProgressoMembroResponse,
  TipoAlteracaoGrupo,
} from './types'

const TIPOS_ALTERACAO: readonly string[] = ['GRUPO', 'ATIVIDADES', 'MEMBROS', 'SOLUCOES'] satisfies TipoAlteracaoGrupo[]

function caminhoDoGrupo(grupoId: string) {
  return `/groups/${encodeURIComponent(grupoId)}`
}

/** Grupos de que o usuário é membro, com o papel dele em cada um (meuPapel). */
export function listarMeusGrupos() {
  return request<GrupoResponse[]>('GET', '/groups')
}

/** Cria o grupo em AGUARDANDO_INICIO; quem cria entra como AVALIADOR. */
export function criarGrupo(dados: GrupoRequest) {
  return request<GrupoResponse>('POST', '/groups', { body: dados })
}

export function buscarGrupo(grupoId: string) {
  return request<GrupoResponse>('GET', caminhoDoGrupo(grupoId))
}

/** Avaliador: AGUARDANDO_INICIO → EM_ANDAMENTO. */
export function iniciarGrupo(grupoId: string) {
  return request<GrupoResponse>('POST', `${caminhoDoGrupo(grupoId)}/start`)
}

/** Avaliador: EM_ANDAMENTO → FINALIZADO. */
export function finalizarGrupo(grupoId: string) {
  return request<GrupoResponse>('POST', `${caminhoDoGrupo(grupoId)}/finish`)
}

export function listarAtividadesDoGrupo(grupoId: string) {
  return request<GrupoAtividadeItemResponse[]>('GET', `${caminhoDoGrupo(grupoId)}/activities`)
}

// Atividades e pesos só mudam com o grupo em AGUARDANDO_INICIO (422 depois)

export function adicionarAtividadeAoGrupo(grupoId: string, dados: GrupoAtividadeRequest) {
  return request<GrupoAtividadeItemResponse>('POST', `${caminhoDoGrupo(grupoId)}/activities`, { body: dados })
}

export function alterarPesoDaAtividade(grupoId: string, atividadeId: string, peso: number) {
  return request<GrupoAtividadeItemResponse>(
    'PATCH',
    `${caminhoDoGrupo(grupoId)}/activities/${encodeURIComponent(atividadeId)}`,
    { body: { peso } },
  )
}

export function removerAtividadeDoGrupo(grupoId: string, atividadeId: string) {
  return request<void>('DELETE', `${caminhoDoGrupo(grupoId)}/activities/${encodeURIComponent(atividadeId)}`)
}

export function listarMembrosDoGrupo(grupoId: string) {
  return request<MembroResponse[]>('GET', `${caminhoDoGrupo(grupoId)}/members`)
}

/** Avaliador remove um ESTUDANTE, só em AGUARDANDO_INICIO. */
export function removerMembroDoGrupo(grupoId: string, usuarioId: string) {
  return request<void>('DELETE', `${caminhoDoGrupo(grupoId)}/members/${encodeURIComponent(usuarioId)}`)
}

/** Placar dos estudantes, para qualquer membro. */
export function buscarProgressoDoGrupo(grupoId: string) {
  return request<ProgressoMembroResponse[]>('GET', `${caminhoDoGrupo(grupoId)}/progress`)
}

/** Avaliador: todos os estudantes. Estudante: só o próprio desempenho. */
export function buscarRelatorioDoGrupo(grupoId: string) {
  return request<DesempenhoEstudanteResponse[]>('GET', `${caminhoDoGrupo(grupoId)}/report`)
}

/**
 * Ouve as alterações do grupo pelo SSE até o sinal abortar ou o servidor encerrar o stream.
 * aoConectar é chamado quando a API confirma a conexão.
 */
export function acompanharGrupo(
  grupoId: string,
  aoAlterar: (tipo: TipoAlteracaoGrupo) => void,
  aoConectar: () => void,
  sinal: AbortSignal,
) {
  return abrirStream(
    `${caminhoDoGrupo(grupoId)}/events`,
    (nome) => {
      if (nome === 'CONECTADO') {
        aoConectar()
      } else if (TIPOS_ALTERACAO.includes(nome)) {
        aoAlterar(nome as TipoAlteracaoGrupo)
      }
    },
    sinal,
  )
}

/** Entra no grupo como ESTUDANTE, só antes do início. Idempotente: quem já é membro só recebe o grupo, com o papel atual. */
export function entrarPorConvite(codigoConvite: string) {
  return request<GrupoResponse>('POST', `/groups/join/${encodeURIComponent(codigoConvite)}`)
}
