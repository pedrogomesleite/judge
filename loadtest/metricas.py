"""Cálculos compartilhados por rodar.py e relatorio.py (só biblioteca padrão)."""
from __future__ import annotations

import csv
import math
import re
import statistics
from pathlib import Path

FASES = ('publicacao_ms', 'fila_ms', 'avaliacao_ms', 'total_ms')
PERCENTIS = (('p50', 0.50), ('p90', 0.90), ('p95', 0.95), ('p99', 0.99), ('p99_9', 0.999))

VEREDITO_ESPERADO = {
    'aceita': 'ACEITA',
    'errada': 'RESPOSTA_ERRADA',
    'pesada': 'ACEITA',
    'tle': 'TEMPO_EXCEDIDO',
    'memoria': 'MEMORIA_EXCEDIDA',
    'compilacao': 'ERRO_COMPILACAO',
}


def percentil(ordenados: list[float], p: float) -> float | None:
    """Interpolação linear entre as amostras vizinhas, igual ao percentile_cont do PostgreSQL e ao padrão do numpy."""
    if not ordenados:
        return None
    k = (len(ordenados) - 1) * p
    baixo = math.floor(k)
    alto = min(baixo + 1, len(ordenados) - 1)
    return ordenados[baixo] + (ordenados[alto] - ordenados[baixo]) * (k - baixo)


def estatisticas(valores) -> dict:
    v = sorted(x for x in valores if x is not None)
    if not v:
        return {'n': 0}
    resultado = {
        'n': len(v),
        'media': statistics.fmean(v),
        'desvio': statistics.stdev(v) if len(v) > 1 else 0.0,
        'min': v[0],
    }
    for nome, p in PERCENTIS:
        resultado[nome] = percentil(v, p)
    resultado['max'] = v[-1]
    resultado['cv'] = resultado['desvio'] / resultado['media'] if resultado['media'] else None
    return resultado


def _numero(texto: str | None) -> float | None:
    return float(texto) if texto not in (None, '') else None


def carregar_solucoes(arquivo: Path) -> list[dict]:
    """Lê o solucoes.csv exportado por sql/solucoes-do-teste.sql, convertendo números e instantes (epoch em s)."""
    with arquivo.open(encoding='utf-8', newline='') as f:
        linhas = list(csv.DictReader(f))
    numericos = ('tempo_ms', 'memoria_kb', 'enviado_em', 'publicada_em', 'iniciada_em', 'finalizada_em') + FASES
    for linha in linhas:
        for campo in numericos:
            linha[campo] = _numero(linha.get(campo))
    return linhas


def ocupacao(solucoes: list[dict], passo_s: float = 1.0) -> list[tuple[float, int, int]]:
    """
    Série (t desde o primeiro envio, soluções na fila, soluções em avaliação), reconstruída pelos instantes gravados na
    API. É exata, ao contrário das estatísticas do RabbitMQ, que o management atualiza a cada 5 s.
    """
    eventos = []
    for s in solucoes:
        fim = s['finalizada_em']
        inicio = s['iniciada_em'] if s['iniciada_em'] is not None else fim
        if s['publicada_em'] is not None:
            eventos.append((s['publicada_em'], 1, 0))
            if inicio is not None:
                eventos.append((inicio, -1, 0))
        if s['iniciada_em'] is not None and fim is not None:
            eventos.append((s['iniciada_em'], 0, 1))
            eventos.append((fim, 0, -1))
    if not eventos:
        return []
    eventos.sort()
    t0 = min(s['enviado_em'] for s in solucoes)
    serie, fila, execucao, i = [], 0, 0, 0
    t = t0
    fim_serie = eventos[-1][0] + passo_s
    while t <= fim_serie:
        while i < len(eventos) and eventos[i][0] <= t:
            fila += eventos[i][1]
            execucao += eventos[i][2]
            i += 1
        serie.append((round(t - t0, 3), fila, execucao))
        t += passo_s
    return serie


def maximo_simultaneo(solucoes: list[dict], inicio: str, fim: str) -> int:
    """Maior número de soluções entre os instantes `inicio` e `fim` ao mesmo tempo (varredura de eventos)."""
    eventos = []
    for s in solucoes:
        a = s[inicio]
        b = s[fim] if s[fim] is not None else s['finalizada_em']
        if a is not None and b is not None:
            eventos.append((a, 1))
            eventos.append((b, -1))
    eventos.sort(key=lambda e: (e[0], e[1]))  # saídas antes das entradas no mesmo instante
    atual = maximo = 0
    for _, delta in eventos:
        atual += delta
        maximo = max(maximo, atual)
    return maximo


def janelas(solucoes: list[dict], largura_s: float) -> list[dict]:
    """Por janela de envio: taxa de chegada, taxa de conclusão e percentis do tempo total (para achar o joelho)."""
    if not solucoes:
        return []
    t0 = min(s['enviado_em'] for s in solucoes)
    fim = max((s['finalizada_em'] or s['enviado_em']) for s in solucoes)
    resultado = []
    inicio = t0
    while inicio <= fim:
        enviadas = [s for s in solucoes if inicio <= s['enviado_em'] < inicio + largura_s]
        concluidas = [s for s in solucoes if s['finalizada_em'] is not None
                      and inicio <= s['finalizada_em'] < inicio + largura_s]
        total = estatisticas(s['total_ms'] for s in enviadas)
        resultado.append({
            'inicio_s': round(inicio - t0, 1),
            'chegadas_por_min': len(enviadas) * 60 / largura_s,
            'concluidas_por_min': len(concluidas) * 60 / largura_s,
            'total_p50_ms': total.get('p50'),
            'total_p95_ms': total.get('p95'),
            'total_p99_ms': total.get('p99'),
        })
        inicio += largura_s
    return resultado


TRANSICAO_S = 5  # mesma transição entre degraus de submissoes.js


def duracao_s(texto: str) -> float:
    """Duração no formato do k6 ("2m", "90s", "1m30s") em segundos."""
    fatores = {'h': 3600, 'm': 60, 's': 1, 'ms': 0.001}
    return sum(float(n) * fatores[u] for n, u in re.findall(r'(\d+(?:\.\d+)?)(ms|h|m|s)', texto))


def degraus_da_rampa(solucoes: list[dict], parametros: dict) -> list[dict]:
    """
    Por degrau da rampa (só a parte estável, sem os 5 s de transição): taxa ofertada, taxa de chegada medida, vazão
    concluída no mesmo intervalo e percentis do tempo total dos envios do degrau. Acima da capacidade, a vazão para de
    acompanhar a taxa ofertada e o tempo total dispara (joelho da curva).
    """
    if not solucoes:
        return []
    inicial, final = int(parametros['TAXA_INICIAL']), int(parametros['TAXA_FINAL'])
    quantidade = int(parametros['DEGRAUS'])
    estavel = duracao_s(parametros['DURACAO_DEGRAU'])
    t0 = min(s['enviado_em'] for s in solucoes)
    resultado = []
    for i in range(quantidade):
        # Mesmo arredondamento do Math.round de submissoes.js
        alvo = final if quantidade == 1 else int(inicial + i * (final - inicial) / (quantidade - 1) + 0.5)
        inicio = t0 + i * (TRANSICAO_S + estavel) + TRANSICAO_S
        fim = inicio + estavel
        enviadas = [s for s in solucoes if inicio <= s['enviado_em'] < fim]
        concluidas = sum(1 for s in solucoes if s['finalizada_em'] is not None and inicio <= s['finalizada_em'] < fim)
        total = estatisticas(s['total_ms'] for s in enviadas)
        resultado.append({
            'degrau': i + 1,
            'taxa_ofertada_por_min': alvo,
            'chegadas_por_min': len(enviadas) * 60 / estavel,
            'concluidas_por_min': concluidas * 60 / estavel,
            'envios': len(enviadas),
            'total_p50_ms': total.get('p50'),
            'total_p95_ms': total.get('p95'),
            'total_p99_ms': total.get('p99'),
            'fila_p95_ms': estatisticas(s['fila_ms'] for s in enviadas).get('p95'),
        })
    return resultado


def resumir_solucoes(solucoes: list[dict], capacidade: int | None, largura_janela_s: float) -> dict:
    """Métricas do lado do servidor de uma execução."""
    finalizadas = [s for s in solucoes if s['finalizada_em'] is not None]
    resumo = {
        'envios': len(solucoes),
        'finalizadas': len(finalizadas),
        'nao_finalizadas': len(solucoes) - len(finalizadas),
        'falhas': sum(1 for s in solucoes if s['status'] == 'FALHA'),
        'fases_ms': {fase.removesuffix('_ms'): estatisticas(s[fase] for s in finalizadas) for fase in FASES},
    }

    inesperados: dict[tuple[str, str], int] = {}
    corretos = 0
    for s in finalizadas:
        if VEREDITO_ESPERADO.get(s['tipo']) == s['veredito']:
            corretos += 1
        else:
            chave = (s['tipo'], s['veredito'])
            inesperados[chave] = inesperados.get(chave, 0) + 1
    resumo['veredito_correto'] = corretos / len(finalizadas) if finalizadas else None
    resumo['vereditos_inesperados'] = [{'tipo': t, 'veredito': v, 'n': n} for (t, v), n in sorted(inesperados.items())]

    tipos = sorted({s['tipo'] for s in solucoes if s['tipo']})
    resumo['por_tipo'] = {
        tipo: {
            'total_ms': estatisticas(s['total_ms'] for s in finalizadas if s['tipo'] == tipo),
            'avaliacao_ms': estatisticas(s['avaliacao_ms'] for s in finalizadas if s['tipo'] == tipo),
            # Tempo de CPU medido pelo isolate (maior entre os casos): deveria variar pouco com a carga
            'tempo_cpu_ms': estatisticas(s['tempo_ms'] for s in finalizadas if s['tipo'] == tipo),
        }
        for tipo in tipos
    }

    if finalizadas:
        primeiro_envio = min(s['enviado_em'] for s in solucoes)
        ultimo_envio = max(s['enviado_em'] for s in solucoes)
        ultima_conclusao = max(s['finalizada_em'] for s in finalizadas)
        makespan = ultima_conclusao - primeiro_envio
        resumo['makespan_s'] = makespan
        resumo['janela_de_envio_s'] = ultimo_envio - primeiro_envio
        resumo['drenagem_s'] = ultima_conclusao - ultimo_envio
        resumo['vazao_por_min'] = len(finalizadas) * 60 / makespan if makespan > 0 else None
        resumo['pico_em_fila'] = maximo_simultaneo(solucoes, 'publicada_em', 'iniciada_em')
        resumo['pico_em_avaliacao'] = maximo_simultaneo(solucoes, 'iniciada_em', 'finalizada_em')
        ocupado = sum(s['avaliacao_ms'] for s in finalizadas if s['avaliacao_ms'] is not None) / 1000
        resumo['utilizacao_boxes'] = ocupado / (capacidade * makespan) if capacidade and makespan > 0 else None
    resumo['janelas'] = janelas(solucoes, largura_janela_s)
    return resumo


def tendencia_k6(resumo_k6: dict, metrica: str) -> dict | None:
    """Converte os valores de uma Trend do resumo JSON do k6 para as mesmas chaves de `estatisticas`."""
    dados = resumo_k6.get('metrics', {}).get(metrica)
    if not dados:
        return None
    v = dados['values']
    chaves = {'count': 'n', 'avg': 'media', 'min': 'min', 'med': 'p50', 'p(90)': 'p90', 'p(95)': 'p95',
              'p(99)': 'p99', 'p(99.9)': 'p99_9', 'max': 'max'}
    return {nova: v.get(antiga) for antiga, nova in chaves.items()}


def valor_k6(resumo_k6: dict, metrica: str, campo: str):
    dados = resumo_k6.get('metrics', {}).get(metrica)
    return dados['values'].get(campo) if dados else None
