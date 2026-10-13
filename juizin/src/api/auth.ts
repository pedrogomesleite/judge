import { request } from './http'
import type { LoginRequest, TokenResponse } from './types'

export function login(dados: LoginRequest) {
  return request<TokenResponse>('POST', '/auth/login', { body: dados, auth: false })
}
