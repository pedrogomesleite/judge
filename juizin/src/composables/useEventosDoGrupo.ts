import { onBeforeUnmount, ref, watch } from 'vue'
import { acompanharGrupo } from '@/api/grupos'
import { ApiError } from '@/api/http'
import type { TipoAlteracaoGrupo } from '@/api/types'

const ESPERA_AGRUPAMENTO_MS = 300
const RECONEXAO_INICIAL_MS = 2000
const RECONEXAO_MAXIMA_MS = 30_000

interface Opcoes {
  /** Tipos alterados, agrupados: várias mudanças seguidas viram uma única chamada. */
  aoAlterar: (tipos: Set<TipoAlteracaoGrupo>) => void
  /** Depois de uma queda, a conexão voltou: eventos podem ter se perdido, então recarregue tudo. */
  aoReconectar: () => void
  /** A API recusou a conexão (403/404): o usuário não é mais membro ou o grupo não existe. */
  aoNegar: (erro: ApiError) => void
}

/**
 * Mantém aberto o SSE do grupo (ADR-003) enquanto o componente estiver montado, com reconexão e espera crescente.
 * Troca de conexão quando o grupoId muda.
 */
export function useEventosDoGrupo(grupoId: () => string, { aoAlterar, aoReconectar, aoNegar }: Opcoes) {
  const conectado = ref(false)

  let controle: AbortController | null = null
  let timerReconexao: ReturnType<typeof setTimeout> | undefined
  let timerAgrupamento: ReturnType<typeof setTimeout> | undefined
  let pendentes = new Set<TipoAlteracaoGrupo>()

  function agrupar(tipo: TipoAlteracaoGrupo) {
    pendentes.add(tipo)
    clearTimeout(timerAgrupamento)
    timerAgrupamento = setTimeout(() => {
      const tipos = pendentes
      pendentes = new Set()
      aoAlterar(tipos)
    }, ESPERA_AGRUPAMENTO_MS)
  }

  function encerrar() {
    controle?.abort()
    controle = null
    clearTimeout(timerReconexao)
    clearTimeout(timerAgrupamento)
    pendentes = new Set()
    conectado.value = false
  }

  function conectar(id: string) {
    encerrar()
    const atual = new AbortController()
    controle = atual
    let espera = RECONEXAO_INICIAL_MS
    let jaConectou = false

    async function abrir() {
      try {
        await acompanharGrupo(
          id,
          agrupar,
          () => {
            conectado.value = true
            espera = RECONEXAO_INICIAL_MS
            if (jaConectou) {
              aoReconectar()
            }
            jaConectou = true
          },
          atual.signal,
        )
      } catch (e) {
        if (e instanceof ApiError && (e.status === 401 || e.status === 403 || e.status === 404)) {
          conectado.value = false
          if (e.status !== 401) {
            aoNegar(e)
          }
          return
        }
      }
      // Stream encerrado pelo servidor (ex.: timeout) ou conexão perdida: tenta de novo
      if (atual.signal.aborted) {
        return
      }
      conectado.value = false
      timerReconexao = setTimeout(abrir, espera)
      espera = Math.min(espera * 2, RECONEXAO_MAXIMA_MS)
    }

    abrir()
  }

  watch(grupoId, conectar, { immediate: true })
  onBeforeUnmount(encerrar)

  return { conectado, encerrar }
}
