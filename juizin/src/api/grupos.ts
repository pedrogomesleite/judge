import { request } from './http'
import type { GrupoAtividadeItemResponse, GrupoResponse } from './types'

/** Grupos de que o usuário é membro, com o papel dele em cada um (meuPapel). */
export function listarMeusGrupos() {
  return request<GrupoResponse[]>('GET', '/groups')
}

export function listarAtividadesDoGrupo(grupoId: string) {
  return request<GrupoAtividadeItemResponse[]>('GET', `/groups/${encodeURIComponent(grupoId)}/activities`)
}
