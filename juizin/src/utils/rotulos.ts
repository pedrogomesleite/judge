import type { PapelGrupo, SolucaoResponse, StatusGrupo, StatusSolucao, Veredito } from '@/api/types'

export type Severidade = 'secondary' | 'success' | 'info' | 'warn' | 'danger' | 'contrast'

export interface Rotulo {
  label: string
  severidade: Severidade
}

export const ROTULOS_VEREDITO: Record<Veredito, Rotulo> = {
  ACEITA: { label: 'Aceita', severidade: 'success' },
  RESPOSTA_ERRADA: { label: 'Resposta errada', severidade: 'danger' },
  TEMPO_EXCEDIDO: { label: 'Tempo excedido', severidade: 'danger' },
  MEMORIA_EXCEDIDA: { label: 'Memória excedida', severidade: 'danger' },
  ERRO_EXECUCAO: { label: 'Erro de execução', severidade: 'danger' },
  ERRO_COMPILACAO: { label: 'Erro de compilação', severidade: 'danger' },
  ERRO_INTERNO: { label: 'Erro interno', severidade: 'danger' },
}

export const ROTULOS_STATUS_SOLUCAO: Record<StatusSolucao, Rotulo> = {
  PENDENTE: { label: 'Na fila', severidade: 'info' },
  EM_EXECUCAO: { label: 'Em execução', severidade: 'info' },
  CONCLUIDA: { label: 'Concluída', severidade: 'secondary' },
  FALHA: { label: 'Falha', severidade: 'danger' },
}

export const ROTULOS_STATUS_GRUPO: Record<StatusGrupo, Rotulo> = {
  AGUARDANDO_INICIO: { label: 'Aguardando início', severidade: 'warn' },
  EM_ANDAMENTO: { label: 'Em andamento', severidade: 'success' },
  FINALIZADO: { label: 'Finalizado', severidade: 'secondary' },
}

export const ROTULOS_PAPEL: Record<PapelGrupo, Rotulo> = {
  AVALIADOR: { label: 'Avaliador', severidade: 'info' },
  ESTUDANTE: { label: 'Estudante', severidade: 'secondary' },
}

export type Classificacao = 'sucesso' | 'erro' | 'andamento'

type EstadoSolucao = Pick<SolucaoResponse, 'status' | 'veredito'>

/** Sucesso = ACEITA; andamento = ainda sem veredito; qualquer outro desfecho (inclusive FALHA) é erro. */
export function classificar(solucao: EstadoSolucao): Classificacao {
  if (solucao.status === 'PENDENTE' || solucao.status === 'EM_EXECUCAO') {
    return 'andamento'
  }
  return solucao.veredito === 'ACEITA' ? 'sucesso' : 'erro'
}

/** O veredito quando já existe; senão, o status. */
export function rotuloSolucao(solucao: EstadoSolucao): Rotulo {
  return solucao.veredito ? ROTULOS_VEREDITO[solucao.veredito] : ROTULOS_STATUS_SOLUCAO[solucao.status]
}
