import { request } from './http'
import type { SolucaoRequest, SolucaoResponse } from './types'

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
