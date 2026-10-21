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

export type TipoCasoDeTeste = 'OFICIAL' | 'COMUNITARIO'

export interface CasoDeTesteRequest {
  entrada: string
  saidaEsperada: string
  tipo: TipoCasoDeTeste
  /** Obrigatório para COMUNITARIO e ignorado para OFICIAL. */
  grupoId: string | null
  /** Só para OFICIAL: um caso comunitário público é recusado (422). */
  publico: boolean
}

export interface CasoDeTesteResponse {
  id: string
  atividadeId: string
  autorId: string
  /** Só nos casos COMUNITARIO: o grupo em que o caso foi compartilhado. */
  grupoId: string | null
  grupoNome: string | null
  tipo: TipoCasoDeTeste
  /** Só nos casos OFICIAL: aparece como exemplo para qualquer pessoa. */
  publico: boolean
  entrada: string
  saidaEsperada: string
}

export interface LinguagemResponse {
  id: string
  nome: string
  versao: string
}

export interface GrupoRequest {
  nome: string
  descricao: string | null
  /** Opcional: o grupo inicia automaticamente nessa data. */
  dataInicio: string | null
  /** Opcional: o grupo finaliza automaticamente nessa data; precisa ser posterior a dataInicio (422). */
  dataFim: string | null
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

export interface GrupoAtividadeRequest {
  atividadeId: string
  ordem: number
  /** 1 a 100; nulo vale 1. */
  peso: number | null
}

export interface GrupoAtividadeItemResponse {
  atividadeId: string
  titulo: string
  ordem: number
  /** Pontos que um envio aceito vale nesta atividade (1 a 100). */
  peso: number
}

/** Placar: atividades com pelo menos um envio aceito (aceites repetidos não somam) sobre o total do grupo. */
export interface ProgressoMembroResponse {
  usuarioId: string
  nome: string
  aceitas: number
  totalAtividades: number
}

export interface DesempenhoAtividadeResponse {
  atividadeId: string
  peso: number
  tentativas: number
  aceita: boolean
  /** O peso, se a atividade foi aceita; senão 0. */
  pontos: number
}

/** Consolidação dos envios de um estudante, com uma linha por atividade na ordem do grupo. */
export interface DesempenhoEstudanteResponse {
  usuarioId: string
  nome: string
  aceitas: number
  pontos: number
  atividades: DesempenhoAtividadeResponse[]
}

/** Nome dos eventos do SSE do grupo: indica o que buscar de novo. */
export type TipoAlteracaoGrupo = 'GRUPO' | 'ATIVIDADES' | 'MEMBROS' | 'SOLUCOES'

export interface MembroResponse {
  usuarioId: string
  nome: string
  papel: PapelGrupo
  entrouEm: string
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

export interface SolucaoCodigoResponse {
  id: string
  linguagem: string
  /** Extensão do arquivo na linguagem da solução, sem o ponto (ex.: cpp). */
  extensao: string
  codigo: string
}

export interface ErroResponse {
  status: number
  mensagem: string
  timestamp: string
  /** Erros de validação por campo; vazio quando não se aplica. */
  campos: Record<string, string>
}
