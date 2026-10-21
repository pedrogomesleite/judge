import { request } from './http'
import type { GrupoAtividadeItemResponse, GrupoResponse, MembroResponse } from './types'

/** Grupos de que o usuário é membro, com o papel dele em cada um (meuPapel). */
export function listarMeusGrupos() {
  return request<GrupoResponse[]>('GET', '/groups')
}

export function listarAtividadesDoGrupo(grupoId: string) {
  return request<GrupoAtividadeItemResponse[]>('GET', `/groups/${encodeURIComponent(grupoId)}/activities`)
}

export function listarMembrosDoGrupo(grupoId: string) {
  return request<MembroResponse[]>('GET', `/groups/${encodeURIComponent(grupoId)}/members`)
}

/** Entra no grupo como ESTUDANTE. Idempotente: quem já é membro só recebe o grupo, com o papel atual. */
export function entrarPorConvite(codigoConvite: string) {
  return request<GrupoResponse>('POST', `/groups/join/${encodeURIComponent(codigoConvite)}`)
}
