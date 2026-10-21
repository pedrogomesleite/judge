import { request } from './http'
import type { CasoDeTesteRequest, CasoDeTesteResponse } from './types'

function caminho(atividadeId: string) {
  return `/activities/${encodeURIComponent(atividadeId)}/test-cases`
}

/** Casos oficiais marcados como públicos (exemplos): não exige login. */
export function listarCasosPublicos(atividadeId: string) {
  return request<CasoDeTesteResponse[]>('GET', `${caminho(atividadeId)}/public`, { auth: false })
}

/** Casos comunitários da atividade compartilhados nos grupos do usuário logado, cada um com o grupo de origem. */
export function listarCasosCompartilhados(atividadeId: string) {
  return request<CasoDeTesteResponse[]>('GET', `${caminho(atividadeId)}/shared`)
}

/** Todos os casos oficiais, públicos ou não: só o autor da atividade. */
export function listarCasosOficiais(atividadeId: string) {
  return request<CasoDeTesteResponse[]>('GET', caminho(atividadeId))
}

/** OFICIAL: só o autor da atividade. COMUNITARIO: membro de um grupo que contém a atividade. */
export function criarCasoDeTeste(atividadeId: string, dados: CasoDeTesteRequest) {
  return request<CasoDeTesteResponse>('POST', caminho(atividadeId), { body: dados })
}

/** Marca ou desmarca um caso oficial como público: só o autor da atividade. */
export function alterarPublico(atividadeId: string, casoId: string, publico: boolean) {
  return request<CasoDeTesteResponse>('PATCH', `${caminho(atividadeId)}/${encodeURIComponent(casoId)}`, {
    body: { publico },
  })
}
