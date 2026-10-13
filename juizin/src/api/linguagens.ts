import { request } from './http'
import type { LinguagemResponse } from './types'

export function listarLinguagens() {
  return request<LinguagemResponse[]>('GET', '/languages', { auth: false })
}
