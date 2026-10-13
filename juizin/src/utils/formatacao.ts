const DATA_HORA = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' })

export function formatarDataHora(iso: string | null | undefined): string {
  return iso ? DATA_HORA.format(new Date(iso)) : '—'
}

export function formatarPeriodo(inicio: string | null, fim: string | null): string {
  if (inicio && fim) {
    return `${formatarDataHora(inicio)} a ${formatarDataHora(fim)}`
  }
  if (inicio) {
    return `A partir de ${formatarDataHora(inicio)}`
  }
  if (fim) {
    return `Até ${formatarDataHora(fim)}`
  }
  return 'Sem datas definidas'
}

/** Valor numérico com unidade, ou travessão quando ainda não há medição. */
export function comUnidade(valor: number | null | undefined, unidade: string): string {
  return valor == null ? '—' : `${valor} ${unidade}`
}
