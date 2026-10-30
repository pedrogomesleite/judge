import { request } from './http'
import type { AtividadeRequest, AtividadeResponse } from './types'

// Catálogo público: não exige login

export function listarAtividades() {
  return request<AtividadeResponse[]>('GET', '/activities', { auth: false })
}

export function buscarAtividade(id: string) {
  return request<AtividadeResponse>('GET', `/activities/${encodeURIComponent(id)}`, { auth: false })
}

/** Qualquer usuário logado; quem cria vira o autor e cadastra os casos de teste oficiais. */
export function criarAtividade(dados: AtividadeRequest) {
  return request<AtividadeResponse>('POST', '/activities', { body: dados })
}
