import { request } from './http'
import type { AtividadeRequest, AtividadeResponse, PermissoesAtividadeResponse } from './types'

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

/** Só o autor, sendo avaliador em algum grupo, e só fora de grupos já iniciados. */
export function editarAtividade(id: string, dados: AtividadeRequest) {
  return request<AtividadeResponse>('PUT', `/activities/${encodeURIComponent(id)}`, { body: dados })
}

/** Só o autor, sendo avaliador em algum grupo, e só se a atividade não estiver em nenhum grupo. */
export function excluirAtividade(id: string) {
  return request<void>('DELETE', `/activities/${encodeURIComponent(id)}`)
}

/** O que o usuário logado pode fazer com a atividade (editar, excluir) e, se não puder, o motivo. */
export function buscarPermissoesAtividade(id: string) {
  return request<PermissoesAtividadeResponse>('GET', `/activities/${encodeURIComponent(id)}/permissions`)
}
