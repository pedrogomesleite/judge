// Separador ";" e BOM UTF-8: o Excel em português abre o arquivo com as colunas e os acentos corretos
const SEPARADOR = ';'
const BOM = '﻿'

export type CelulaCsv = string | number

function celula(valor: CelulaCsv): string {
  let texto = String(valor)
  // Nomes e títulos vêm dos usuários: um texto começando com = + - @ viraria fórmula na planilha
  if (typeof valor === 'string' && /^[=+\-@\t\r]/.test(texto)) {
    texto = `'${texto}`
  }
  return /[";\r\n]/.test(texto) ? `"${texto.replace(/"/g, '""')}"` : texto
}

/** Monta o conteúdo de um CSV; a primeira linha costuma ser o cabeçalho. */
export function gerarCsv(linhas: CelulaCsv[][]): string {
  return BOM + linhas.map((linha) => linha.map(celula).join(SEPARADOR)).join('\r\n') + '\r\n'
}

export const TIPO_CSV = 'text/csv;charset=utf-8'
