import { request } from './http'
import type { AtividadeResponse } from './types'

// Catálogo público: não exige login

export function listarAtividades() {
  return request<AtividadeResponse[]>('GET', '/activities', { auth: false })
}

export function buscarAtividade(id: string) {
  return request<AtividadeResponse>('GET', `/activities/${encodeURIComponent(id)}`, { auth: false })
}
