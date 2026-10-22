import { onBeforeUnmount } from 'vue'
import { acompanharSolucao } from '@/api/solucoes'
import { ApiError } from '@/api/http'
import type { SolucaoResponse, StatusSolucao } from '@/api/types'
import { classificar } from '@/utils/rotulos'

const RECONEXAO_INICIAL_MS = 2000
const RECONEXAO_MAXIMA_MS = 30_000

/** Ordem do ciclo de vida da solução (docs/architecture.md, 4.1). */
const ETAPA: Record<StatusSolucao, number> = { PENDENTE: 0, EM_EXECUCAO: 1, CONCLUIDA: 2, FALHA: 2 }

interface Opcoes {
  /** Novo estado da solução. O status nunca regride: um evento atrasado é ignorado. */
  aoAtualizar: (solucao: SolucaoResponse) => void
  /** A API recusou a conexão (403/404): o usuário não pode ver a solução, ou ela não existe. */
  aoNegar: (erro: ApiError) => void
}

/**
 * Acompanha uma solução pelo SSE (ADR-003) até o veredito, reconectando com espera crescente. O primeiro evento de cada
 * conexão é o estado atual, então a reconexão já recupera o que se perdeu. Encerra ao desmontar o componente.
 */
export function useEventosDaSolucao({ aoAtualizar, aoNegar }: Opcoes) {
  let controle: AbortController | null = null
  let timerReconexao: ReturnType<typeof setTimeout> | undefined

  function encerrar() {
    controle?.abort()
    controle = null
    clearTimeout(timerReconexao)
  }

  function acompanhar(inicial: SolucaoResponse) {
    encerrar()
    const atual = new AbortController()
    controle = atual
    let ultima = inicial
    let espera = RECONEXAO_INICIAL_MS

    function atualizar(solucao: SolucaoResponse) {
      if (atual.signal.aborted || ETAPA[solucao.status] < ETAPA[ultima.status]) {
        return
      }
      ultima = solucao
      espera = RECONEXAO_INICIAL_MS
      aoAtualizar(solucao)
    }

    async function abrir() {
      try {
        await acompanharSolucao(inicial.id, atualizar, atual.signal)
      } catch (e) {
        if (e instanceof ApiError && (e.status === 401 || e.status === 403 || e.status === 404)) {
          if (e.status !== 401) {
            aoNegar(e)
          }
          return
        }
      }
      // Stream encerrado: depois do veredito, acabou; senão (timeout ou conexão perdida), tenta de novo
      if (atual.signal.aborted || classificar(ultima) !== 'andamento') {
        return
      }
      timerReconexao = setTimeout(abrir, espera)
      espera = Math.min(espera * 2, RECONEXAO_MAXIMA_MS)
    }

    if (classificar(inicial) === 'andamento') {
      abrir()
    }
  }

  onBeforeUnmount(encerrar)

  return { acompanhar, encerrar }
}
