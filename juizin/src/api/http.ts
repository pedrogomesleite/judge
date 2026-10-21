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

/**
 * Abre um stream SSE autenticado e chama aoEvento para cada evento recebido. Usa fetch em vez de EventSource,
 * que não envia o header Authorization. Resolve quando o servidor encerra o stream; rejeita com ApiError se a
 * conexão falhar ou a API recusar (ex.: 403). Cancelado pelo sinal, a promise resolve sem erro.
 */
export async function abrirStream(
  caminho: string,
  aoEvento: (nome: string, dados: string) => void,
  sinal: AbortSignal,
): Promise<void> {
  // application/json junto: os erros da API (ErroResponse) chegam legíveis
  const headers: Record<string, string> = { Accept: 'text/event-stream, application/json' }
  const token = obterToken()
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  let resposta: Response
  try {
    resposta = await fetch(`${BASE_URL}${caminho}`, { headers, signal: sinal, cache: 'no-store' })
  } catch {
    if (sinal.aborted) {
      return
    }
    throw new ApiError(0, 'Não foi possível conectar à API')
  }

  if (resposta.status === 401) {
    aoNaoAutorizado()
  }
  if (!resposta.ok || !resposta.body) {
    throw await lerErro(resposta)
  }

  const leitor = resposta.body.pipeThrough(new TextDecoderStream()).getReader()
  let pendente = ''
  try {
    for (;;) {
      const { value, done } = await leitor.read()
      if (done) {
        return
      }
      // Eventos SSE terminam com uma linha em branco
      pendente += value.replace(/\r\n?/g, '\n')
      let fim: number
      while ((fim = pendente.indexOf('\n\n')) >= 0) {
        const bloco = pendente.slice(0, fim)
        pendente = pendente.slice(fim + 2)
        const evento = lerEventoSse(bloco)
        if (evento) {
          aoEvento(evento.nome, evento.dados)
        }
      }
    }
  } catch {
    if (sinal.aborted) {
      return
    }
    throw new ApiError(0, 'Conexão com a API interrompida')
  }
}

/** Campos event e data de um bloco SSE; comentários (": ...") são ignorados. */
function lerEventoSse(bloco: string): { nome: string; dados: string } | null {
  let nome = 'message'
  const dados: string[] = []
  for (const linha of bloco.split('\n')) {
    if (linha.startsWith('event:')) {
      nome = linha.slice(6).trim()
    } else if (linha.startsWith('data:')) {
      dados.push(linha.slice(5).replace(/^ /, ''))
    }
  }
  return dados.length > 0 ? { nome, dados: dados.join('\n') } : null
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
