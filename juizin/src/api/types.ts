// Espelham os records e enums da API (src/main/java/com/packges/judge/dto e enums).
// Datas (Instant) chegam como strings ISO-8601.

export type StatusGrupo = 'AGUARDANDO_INICIO' | 'EM_ANDAMENTO' | 'FINALIZADO'

export type PapelGrupo = 'AVALIADOR' | 'ESTUDANTE'

export type StatusSolucao = 'PENDENTE' | 'EM_EXECUCAO' | 'CONCLUIDA' | 'FALHA'

export type Veredito =
  | 'ACEITA'
  | 'RESPOSTA_ERRADA'
  | 'TEMPO_EXCEDIDO'
  | 'MEMORIA_EXCEDIDA'
  | 'ERRO_EXECUCAO'
  | 'ERRO_COMPILACAO'
  | 'ERRO_INTERNO'

export interface LoginRequest {
  login: string
  senha: string
}

export interface TokenResponse {
  token: string
  expiresAt: string
}

export interface UsuarioCreateRequest {
  nome: string
  login: string
  senha: string
}

export interface UsuarioResponse {
  id: string
  nome: string
  login: string
}

export interface AtividadeResponse {
  id: string
  autorId: string
  titulo: string
  enunciado: string
  limiteTempoMs: number
  limiteMemoriaMb: number
  criadoEm: string
}

export interface LinguagemResponse {
  id: string
  nome: string
  versao: string
}

export interface GrupoResponse {
  id: string
  nome: string
  descricao: string | null
  status: StatusGrupo
  dataInicio: string | null
  dataFim: string | null
  codigoConvite: string
  meuPapel: PapelGrupo
}

export interface GrupoAtividadeItemResponse {
  atividadeId: string
  titulo: string
  ordem: number
}

export interface SolucaoRequest {
  atividadeId: string
  linguagemId: string
  /** Nulo para soluções avulsas (fora de grupo). */
  grupoId: string | null
  codigo: string
}

export interface SolucaoResponse {
  id: string
  autorId: string
  atividadeId: string
  grupoId: string | null
  linguagem: string
  status: StatusSolucao
  veredito: Veredito | null
  tempoMs: number | null
  memoriaKb: number | null
  enviadoEm: string
}

export interface ErroResponse {
  status: number
  mensagem: string
  timestamp: string
  /** Erros de validação por campo; vazio quando não se aplica. */
  campos: Record<string, string>
}
