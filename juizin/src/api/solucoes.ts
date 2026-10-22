import { abrirStream, request } from './http'
import type { SolucaoCodigoResponse, SolucaoRequest, SolucaoResponse, StatusSolucao } from './types'

const STATUS_SOLUCAO: readonly string[] = ['PENDENTE', 'EM_EXECUCAO', 'CONCLUIDA', 'FALHA'] satisfies StatusSolucao[]

/** Responde 202 com a solução PENDENTE; o veredito chega depois (ver acompanharSolucao). */
export function enviarSolucao(dados: SolucaoRequest) {
  return request<SolucaoResponse>('POST', '/submissions', { body: dados })
}

export function buscarSolucao(id: string) {
  return request<SolucaoResponse>('GET', `/submissions/${encodeURIComponent(id)}`)
}

/**
 * SSE da solução (ADR-003): cada evento traz o estado completo, e o nome do evento é o status. O primeiro evento é o
 * estado no momento da conexão, e a API encerra o stream depois do veredito. Mesmas permissões de buscarSolucao.
 */
export function acompanharSolucao(id: string, aoAtualizar: (solucao: SolucaoResponse) => void, sinal: AbortSignal) {
  return abrirStream(
    `/submissions/${encodeURIComponent(id)}/events`,
    (nome, dados) => {
      if (STATUS_SOLUCAO.includes(nome)) {
        aoAtualizar(JSON.parse(dados) as SolucaoResponse)
      }
    },
    sinal,
  )
}

/** Soluções do próprio usuário, das mais recentes para as mais antigas. */
export function listarMinhasSolucoes() {
  return request<SolucaoResponse[]>('GET', '/submissions')
}

/** Avaliador: todas as soluções do grupo. Estudante: só as próprias. Das mais recentes para as mais antigas. */
export function listarSolucoesDoGrupo(grupoId: string) {
  return request<SolucaoResponse[]>('GET', `/submissions?groupId=${encodeURIComponent(grupoId)}`)
}

/** Código enviado; só para o autor e os avaliadores do grupo da solução. */
export function buscarCodigoSolucao(id: string) {
  return request<SolucaoCodigoResponse>('GET', `/submissions/${encodeURIComponent(id)}/code`)
}
