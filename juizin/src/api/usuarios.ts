import { request } from './http'
import type { UsuarioCreateRequest, UsuarioResponse } from './types'

export function cadastrarUsuario(dados: UsuarioCreateRequest) {
  return request<UsuarioResponse>('POST', '/users', { body: dados, auth: false })
}

export function buscarUsuarioAtual() {
  return request<UsuarioResponse>('GET', '/users/me')
}
