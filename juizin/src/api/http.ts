import type { ErroResponse } from './types'

const BASE_URL = '/api'

type Metodo = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

interface Opcoes {
  body?: unknown
  /**
   * false nos endpoints públicos: não envia o token. Um token expirado num endpoint
   * permitAll faria o resource server do Spring responder 401.
   */
  auth?: boolean
}

/** Erro devolvido pela API (ErroResponse) ou falha de conexão (status 0). */
export class ApiError extends Error {
  readonly status: number
  readonly campos: Record<string, string>

  constructor(status: number, mensagem: string, campos: Record<string, string> = {}) {
    super(mensagem)
    this.name = 'ApiError'
    this.status = status
    this.campos = campos
  }
}

let obterToken: () => string | null = () => null
let aoNaoAutorizado: () => void = () => {}

/**
 * Liga o cliente à sessão sem importar o store aqui (evita import circular).
 * aoNaoAutorizado é chamado quando uma request autenticada recebe 401.
 */
export function configurarAutenticacao(getToken: () => string | null, onUnauthorized: () => void) {
  obterToken = getToken
  aoNaoAutorizado = onUnauthorized
}

export async function request<T>(metodo: Metodo, caminho: string, { body, auth = true }: Opcoes = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  const token = auth ? obterToken() : null
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  let resposta: Response
  try {
    resposta = await fetch(`${BASE_URL}${caminho}`, {
      method: metodo,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, 'Não foi possível conectar à API')
  }

  if (resposta.status === 401 && auth) {
    aoNaoAutorizado()
  }
  if (!resposta.ok) {
    throw await lerErro(resposta)
  }
  if (resposta.status === 204) {
    return undefined as T
  }
  return (await resposta.json()) as T
}

export function mensagemDeErro(erro: unknown): string {
  return erro instanceof ApiError ? erro.message : 'Erro inesperado'
}

async function lerErro(resposta: Response): Promise<ApiError> {
  try {
    const erro = (await resposta.json()) as ErroResponse
    return new ApiError(resposta.status, erro.mensagem, erro.campos ?? {})
  } catch {
    return new ApiError(resposta.status, mensagemPadrao(resposta.status))
  }
}

function mensagemPadrao(status: number): string {
  switch (status) {
    case 401:
      return 'Sessão expirada. Entre novamente.'
    case 403:
      return 'Sem permissão para esta ação'
    case 404:
      return 'Não encontrado'
    case 502:
    case 503:
    case 504:
      return 'A API está fora do ar'
    default:
      return `Erro ${status} na API`
  }
}
