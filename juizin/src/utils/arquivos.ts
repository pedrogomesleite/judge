/** Trecho seguro para nome de arquivo: sem acentos, minúsculo e separado por hífens. */
export function paraNomeDeArquivo(texto: string): string {
  return texto
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
}

/** Data e hora locais no formato AAAAMMDD-HHMMSS, para distinguir arquivos do mesmo autor. */
export function carimboDeArquivo(iso: string): string {
  const data = new Date(iso)
  const dois = (n: number) => String(n).padStart(2, '0')
  return (
    `${data.getFullYear()}${dois(data.getMonth() + 1)}${dois(data.getDate())}` +
    `-${dois(data.getHours())}${dois(data.getMinutes())}${dois(data.getSeconds())}`
  )
}

/** Baixa um texto como arquivo, gerado no próprio navegador. */
export function baixarTexto(nomeArquivo: string, conteudo: string) {
  const url = URL.createObjectURL(new Blob([conteudo], { type: 'text/plain;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url
  link.download = nomeArquivo
  document.body.append(link)
  link.click()
  link.remove()
  // Libera depois: alguns navegadores só começam o download após o clique terminar
  setTimeout(() => URL.revokeObjectURL(url), 0)
}
