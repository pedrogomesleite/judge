import { request } from './http'
import type { SolucaoCodigoResponse, SolucaoRequest, SolucaoResponse } from './types'

/** Responde 202 com a solução PENDENTE; o veredito chega depois (ver buscarSolucao). */
export function enviarSolucao(dados: SolucaoRequest) {
  return request<SolucaoResponse>('POST', '/submissions', { body: dados })
}

export function buscarSolucao(id: string) {
  return request<SolucaoResponse>('GET', `/submissions/${encodeURIComponent(id)}`)
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
