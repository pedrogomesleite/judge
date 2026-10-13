import { onBeforeUnmount } from 'vue'

/**
 * Repete `atualizar` a cada `intervaloMs` enquanto `continuar()` for verdadeiro. Para ao desmontar o componente.
 * `atualizar` deve tratar os próprios erros; se lançar, o polling para.
 */
// TODO(sse): trocar o polling pela notificação via SSE (ADR-003)
export function usePolling(atualizar: () => Promise<void>, continuar: () => boolean, intervaloMs: number) {
  let timer: ReturnType<typeof setTimeout> | undefined
  let ativo = true

  function agendar() {
    clearTimeout(timer)
    if (!ativo || !continuar()) {
      return
    }
    timer = setTimeout(async () => {
      await atualizar()
      agendar()
    }, intervaloMs)
  }

  onBeforeUnmount(() => {
    ativo = false
    clearTimeout(timer)
  })

  return { agendar }
}
