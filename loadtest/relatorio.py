#!/usr/bin/env python3
"""
Consolida as execuções de loadtest/resultados/ em relatorio.md e relatorio.csv (tabelas prontas para o artigo) e, se o
matplotlib estiver instalado, gera os gráficos em loadtest/resultados/graficos/.

    python3 loadtest/relatorio.py                       # todas as execuções
    python3 loadtest/relatorio.py --grupos 'pico-r.*'   # só os grupos que casam com a expressão

Roda no Windows ou no WSL (não precisa do Docker). Para os gráficos: pip install matplotlib
"""
from __future__ import annotations

import argparse
import csv
import json
import re
import statistics
from pathlib import Path

import metricas

RESULTADOS = Path(__file__).resolve().parent / 'resultados'

# Paleta categórica validada (ordem fixa, modo claro; ver a skill de dataviz) e tinta do gráfico
SERIES = ['#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#008300', '#6250d6', '#e34948']
SUPERFICIE, TINTA, TINTA_SECUNDARIA, TINTA_SUAVE = '#fcfcfb', '#0b0b0b', '#52514e', '#898781'
GRADE, EIXO = '#e1e0d9', '#c3c2b7'


def carregar(filtro: str | None) -> list[tuple[Path, dict]]:
    execucoes = []
    for pasta in sorted(RESULTADOS.iterdir()) if RESULTADOS.exists() else []:
        arquivo = pasta / 'resumo.json'
        if not arquivo.exists():
            continue
        resumo = json.loads(arquivo.read_text(encoding='utf-8'))
        if filtro and not re.fullmatch(filtro, resumo['grupo']):
            continue
        execucoes.append((pasta, resumo))
    return execucoes


def _f(valor, casas: int = 0) -> str:
    return '' if valor is None else f'{valor:.{casas}f}'


def _pct(valor) -> str:
    return '' if valor is None else f'{valor:.2%}'


def _cpu(resumo: dict, trecho: str, campo: str = 'cpu_medio_pct') -> float | None:
    """CPU somada dos containers cujo nome contém `trecho` (100% = 1 núcleo)."""
    valores = [r[campo] for nome, r in resumo.get('recursos', {}).items() if trecho in nome]
    return sum(valores) if valores else None


def _parametro(resumo: dict) -> str:
    p = resumo.get('parametros', {})
    if resumo['cenario'] == 'carga':
        return f'{p.get("TAXA")}/min por {p.get("DURACAO")}'
    if resumo['cenario'] == 'rampa':
        return f'{p.get("TAXA_INICIAL")}→{p.get("TAXA_FINAL")}/min, {p.get("DEGRAUS")}×{p.get("DURACAO_DEGRAU")}'
    if resumo['cenario'] in ('baseline', 'pico'):
        return f'{p.get("ENVIOS")} envios'
    return ''


LINHA_ENVIO = [
    ('execucao', lambda r: r['run_id']),
    ('cenario', lambda r: r['cenario']),
    ('parametros', _parametro),
    ('runners', lambda r: r.get('runners')),
    ('boxes', lambda r: r.get('boxes')),
    ('capacidade', lambda r: r.get('capacidade')),
    ('envios', lambda r: r['envios']),
    ('vazao_por_min', lambda r: _f(r.get('vazao_por_min'), 1)),
    ('total_p50_ms', lambda r: _f(r['fases_ms']['total'].get('p50'))),
    ('total_p90_ms', lambda r: _f(r['fases_ms']['total'].get('p90'))),
    ('total_p95_ms', lambda r: _f(r['fases_ms']['total'].get('p95'))),
    ('total_p99_ms', lambda r: _f(r['fases_ms']['total'].get('p99'))),
    ('total_max_ms', lambda r: _f(r['fases_ms']['total'].get('max'))),
    ('fila_p95_ms', lambda r: _f(r['fases_ms']['fila'].get('p95'))),
    ('fila_p99_ms', lambda r: _f(r['fases_ms']['fila'].get('p99'))),
    ('avaliacao_p50_ms', lambda r: _f(r['fases_ms']['avaliacao'].get('p50'))),
    ('avaliacao_p95_ms', lambda r: _f(r['fases_ms']['avaliacao'].get('p95'))),
    ('publicacao_p99_ms', lambda r: _f(r['fases_ms']['publicacao'].get('p99'))),
    ('cliente_envio_p99_ms', lambda r: _f((r.get('cliente') or {}).get('envio_ms', {}).get('p99'))),
    ('cliente_total_p95_ms', lambda r: _f(((r.get('cliente') or {}).get('tempo_total_ms') or {}).get('p95'))),
    ('cliente_total_p99_ms', lambda r: _f(((r.get('cliente') or {}).get('tempo_total_ms') or {}).get('p99'))),
    ('veredito_correto', lambda r: _pct(r.get('veredito_correto'))),
    ('falhas', lambda r: r['falhas']),
    ('nao_finalizadas', lambda r: r['nao_finalizadas']),
    ('pico_em_fila', lambda r: r.get('pico_em_fila')),
    ('drenagem_s', lambda r: _f(r.get('drenagem_s'))),
    ('utilizacao_boxes', lambda r: _pct(r.get('utilizacao_boxes'))),
    ('cpu_runners_pct', lambda r: _f(_cpu(r, 'runner-cpp'))),
    ('cpu_api_pct', lambda r: _f(_cpu(r, '-api-'))),
    # Pico de CPU do gerador: perto de 100% × núcleos livres, o k6 pode ter limitado a carga
    ('cpu_k6_max_pct', lambda r: _f(_cpu(r, '-k6-', 'cpu_max_pct'))),
    ('cpu_pesada_cv', lambda r: _pct(r.get('por_tipo', {}).get('pesada', {}).get('tempo_cpu_ms', {}).get('cv'))),
]


def tabela_md(cabecalho: list[str], linhas: list[list]) -> str:
    saida = ['| ' + ' | '.join(cabecalho) + ' |', '|' + '|'.join(':---' if i == 0 else '---:' for i in range(len(cabecalho))) + '|']
    saida += ['| ' + ' | '.join('' if c is None else str(c) for c in linha) + ' |' for linha in linhas]
    return '\n'.join(saida)


def media_desvio(valores: list[float], casas: int = 0) -> str:
    valores = [v for v in valores if v is not None]
    if not valores:
        return ''
    if len(valores) == 1:
        return f'{valores[0]:.{casas}f}'
    return f'{statistics.fmean(valores):.{casas}f} ± {statistics.stdev(valores):.{casas}f}'


def agrupar(envios: list[dict]) -> dict[str, list[dict]]:
    grupos: dict[str, list[dict]] = {}
    for r in envios:
        grupos.setdefault(r['grupo'], []).append(r)
    return grupos


def escalabilidade(envios: list[dict]) -> list[list]:
    """Speedup e eficiência por capacidade (réplicas × boxes), por cenário e parâmetros iguais."""
    linhas = []
    familias: dict[tuple, dict[int, list[float]]] = {}
    for r in envios:
        if r.get('capacidade') and r.get('vazao_por_min'):
            chave = (r['cenario'], _parametro(r), r.get('mistura'))
            familias.setdefault(chave, {}).setdefault(r['capacidade'], []).append(r['vazao_por_min'])
    for (cenario, parametro, _), por_capacidade in familias.items():
        if len(por_capacidade) < 2:
            continue
        base_c = min(por_capacidade)
        base_x = statistics.fmean(por_capacidade[base_c])
        for c in sorted(por_capacidade):
            x = statistics.fmean(por_capacidade[c])
            speedup = x / base_x
            linhas.append([cenario, parametro, c, len(por_capacidade[c]), media_desvio(por_capacidade[c], 1),
                           f'{speedup:.2f}', f'{speedup / (c / base_c):.0%}'])
    return linhas


def escrever_relatorio(execucoes: list[tuple[Path, dict]]) -> None:
    envios = [r for _, r in execucoes if r['cenario'] != 'leitura']
    leituras = [r for _, r in execucoes if r['cenario'] == 'leitura']
    partes = ['# Testes de carga do Juizin', '',
              'Gerado por `loadtest/relatorio.py`. Tempos em ms; percentis por interpolação linear '
              '(como o `percentile_cont` do PostgreSQL). Metodologia em `loadtest/README.md`.', '']

    if envios:
        with (RESULTADOS / 'relatorio.csv').open('w', encoding='utf-8', newline='') as f:
            escritor = csv.writer(f)
            escritor.writerow([nome for nome, _ in LINHA_ENVIO])
            for r in envios:
                escritor.writerow([extrair(r) for _, extrair in LINHA_ENVIO])

        partes += ['## Resumo por configuração (média ± desvio entre repetições)', '']
        linhas = []
        for grupo, rs in agrupar(envios).items():
            total = [r['fases_ms']['total'] for r in rs]
            linhas.append([grupo, len(rs), _parametro(rs[0]), rs[0].get('capacidade'),
                           media_desvio([t.get('p50') for t in total]), media_desvio([t.get('p95') for t in total]),
                           media_desvio([t.get('p99') for t in total]),
                           media_desvio([r['fases_ms']['fila'].get('p99') for r in rs]),
                           media_desvio([r.get('vazao_por_min') for r in rs], 1),
                           _pct(statistics.fmean([r.get('veredito_correto') or 0 for r in rs])),
                           sum(r['falhas'] + r['nao_finalizadas'] for r in rs)])
        partes += [tabela_md(['grupo', 'rep.', 'carga', 'capac.', 'total p50', 'total p95', 'total p99', 'fila p99',
                              'vazão/min', 'veredito ok', 'falhas+perdas'], linhas), '']

        partes += ['## Decomposição do tempo total por fase (p50 / p95 / p99)', '',
                   'publicação = commit + publish com confirm; fila = espera em `submissions.cpp`; '
                   'avaliação = casos de teste + compilação + execução no isolate.', '']
        linhas = []
        for r in envios:
            celulas = [r['run_id']]
            for fase in ('publicacao', 'fila', 'avaliacao', 'total'):
                e = r['fases_ms'][fase]
                celulas.append(f'{_f(e.get("p50"))} / {_f(e.get("p95"))} / {_f(e.get("p99"))}')
            linhas.append(celulas)
        partes += [tabela_md(['execução', 'publicação', 'fila', 'avaliação', 'total'], linhas), '']

        partes += ['## Tempo de CPU medido pelo isolate por tipo de solução (média ± desvio, CV)', '',
                   'Para o mesmo código, o tempo medido deveria variar pouco com a carga; um CV que cresce com a '
                   'carga indica interferência entre execuções (vizinho barulhento).', '']
        linhas = []
        for r in envios:
            for tipo, dados in r.get('por_tipo', {}).items():
                cpu = dados['tempo_cpu_ms']
                if cpu.get('n'):
                    linhas.append([r['run_id'], tipo, cpu['n'], f'{cpu["media"]:.1f} ± {cpu["desvio"]:.1f}',
                                   _pct(cpu.get('cv')), _f(dados['avaliacao_ms'].get('p50'))])
        partes += [tabela_md(['execução', 'tipo', 'n', 'CPU (ms)', 'CV', 'avaliação p50'], linhas), '']

        linhas = escalabilidade(envios)
        if linhas:
            partes += ['## Escalabilidade (vazão por capacidade = réplicas × boxes)', '',
                       'speedup = vazão / vazão da menor capacidade; eficiência = speedup / aumento da capacidade.', '']
            partes += [tabela_md(['cenário', 'carga', 'capacidade', 'rep.', 'vazão/min', 'speedup', 'eficiência'],
                                 linhas), '']

        linhas = []
        for pasta, r in execucoes:
            if r['cenario'] == 'rampa' and (pasta / 'solucoes.csv').exists():
                for d in degraus(pasta, r):
                    linhas.append([r['run_id'], d['degrau'], d['taxa_ofertada_por_min'], _f(d['chegadas_por_min'], 1),
                                   _f(d['concluidas_por_min'], 1), _f(d['total_p50_ms']), _f(d['total_p95_ms']),
                                   _f(d['total_p99_ms']), _f(d['fila_p95_ms'])])
        if linhas:
            partes += ['## Rampa por degrau (ponto de saturação)', '',
                       'Só a parte estável de cada degrau. Quando "concluídas/min" para de acompanhar a taxa ofertada, '
                       'a capacidade foi atingida.', '']
            partes += [tabela_md(['execução', 'degrau', 'ofertada/min', 'chegadas/min', 'concluídas/min',
                                  'total p50', 'total p95', 'total p99', 'fila p95'], linhas), '']

        inesperados = [(r['run_id'], i) for r in envios for i in r.get('vereditos_inesperados', [])]
        if inesperados:
            partes += ['## Vereditos diferentes do esperado', '',
                       tabela_md(['execução', 'tipo', 'veredito', 'n'],
                                 [[run, i['tipo'], i['veredito'], i['n']] for run, i in inesperados]), '']

        eventos = [(r['run_id'], e) for r in envios for e in r.get('eventos', [])]
        if eventos:
            partes += ['## Eventos de falha injetada', '',
                       tabela_md(['execução', 'evento', 'container'],
                                 [[run, e['evento'], e['container']] for run, e in eventos]), '']

    if leituras:
        partes += ['## Leitura do catálogo (síncrono)', '']
        linhas = []
        for r in leituras:
            for nome, e in [('todas', r['http_req_duration'])] + list(r['por_endpoint'].items()):
                if e:
                    linhas.append([r['run_id'], nome, _f(r.get('requisicoes_por_s'), 1) if nome == 'todas' else '',
                                   _f(e.get('p50'), 1), _f(e.get('p95'), 1), _f(e.get('p99'), 1), _f(e.get('max'), 1),
                                   _pct(r.get('taxa_de_erro')) if nome == 'todas' else ''])
        partes += [tabela_md(['execução', 'endpoint', 'req/s', 'p50', 'p95', 'p99', 'máx', 'erros'], linhas), '']

    (RESULTADOS / 'relatorio.md').write_text('\n'.join(partes), encoding='utf-8')
    print(f'Relatório: {RESULTADOS / "relatorio.md"}' + (f' e {RESULTADOS / "relatorio.csv"}' if envios else ''))


# --- Gráficos ---------------------------------------------------------------------------------------------------------

def estilo(plt) -> None:
    plt.rcParams.update({
        'figure.facecolor': SUPERFICIE, 'axes.facecolor': SUPERFICIE, 'savefig.facecolor': SUPERFICIE,
        'font.family': 'sans-serif', 'font.sans-serif': ['Segoe UI', 'Helvetica', 'Arial', 'DejaVu Sans'],
        'font.size': 10, 'text.color': TINTA, 'axes.labelcolor': TINTA_SECUNDARIA,
        'axes.edgecolor': EIXO, 'axes.linewidth': 0.8, 'axes.spines.top': False, 'axes.spines.right': False,
        'axes.grid': True, 'grid.color': GRADE, 'grid.linewidth': 0.6, 'axes.axisbelow': True,
        'xtick.color': TINTA_SUAVE, 'ytick.color': TINTA_SUAVE, 'xtick.labelcolor': TINTA_SECUNDARIA,
        'ytick.labelcolor': TINTA_SECUNDARIA, 'lines.linewidth': 1.6, 'lines.markersize': 6,
        'legend.frameon': False, 'axes.titlesize': 11, 'axes.titleweight': 'bold', 'axes.titlelocation': 'left',
    })


def salvar(fig, caminho: Path) -> None:
    fig.tight_layout()
    fig.savefig(caminho, dpi=200)
    fig.clf()
    print(f'Gráfico: {caminho}')


def grafico_cdf(plt, grupos: dict[str, list[Path]], destino: Path) -> None:
    """Distribuição acumulada do tempo total, uma curva por grupo (repetições somadas)."""
    nomes = list(grupos)[:len(SERIES)]
    if len(grupos) > len(SERIES):
        print(f'CDF: só os {len(SERIES)} primeiros grupos (use --grupos para escolher)')
    fig, ax = plt.subplots(figsize=(7.5, 4.2))
    for i, nome in enumerate(nomes):
        valores = sorted(s['total_ms'] / 1000 for pasta in grupos[nome]
                         for s in metricas.carregar_solucoes(pasta / 'solucoes.csv') if s['total_ms'] is not None)
        if not valores:
            continue
        y = [(k + 1) / len(valores) for k in range(len(valores))]
        ax.step(valores, y, where='post', color=SERIES[i], label=nome)
        if len(nomes) <= 4:
            # Rótulo direto sobre a própria curva, cada um num quantil diferente para não colidir
            q = 0.30 + 0.14 * i
            ax.annotate(nome, (metricas.percentil(valores, q), q), xytext=(6, -2), textcoords='offset points',
                        fontsize=8.5, color=TINTA_SECUNDARIA)
    for p in (0.95, 0.99):
        ax.axhline(p, color=TINTA_SUAVE, linewidth=0.8, linestyle=(0, (3, 3)))
        ax.annotate(f'p{int(p * 100)}', (1, p), xycoords=('axes fraction', 'data'), xytext=(-2, 3),
                    textcoords='offset points', ha='right', fontsize=8, color=TINTA_SUAVE)
    ax.set_xscale('log')
    # Marcas em valores redondos (0,2 · 0,5 · 1 · 2 · 5 s...) e sem rótulos nas marcas menores, que colidem
    from matplotlib.ticker import FixedLocator, FuncFormatter, NullFormatter
    ax.xaxis.set_major_locator(FixedLocator([m * 10 ** e for e in range(-3, 4) for m in (1, 2, 5)]))
    ax.xaxis.set_major_formatter(FuncFormatter(lambda v, _: f'{v:g}'.replace('.', ',')))
    ax.xaxis.set_minor_formatter(NullFormatter())
    ax.set_xlabel('tempo total, envio → veredito (s, escala log)')
    ax.set_ylabel('fração das soluções')
    ax.set_ylim(0, 1.01)
    ax.set_title('Distribuição do tempo total por configuração')
    if len(nomes) >= 2:
        ax.legend(loc='lower right', fontsize=8.5)
    salvar(fig, destino)


def grafico_ocupacao(plt, pasta: Path, resumo: dict) -> None:
    """Soluções na fila e em avaliação ao longo do tempo, reconstruídas pelos instantes gravados na API."""
    serie = metricas.ocupacao(metricas.carregar_solucoes(pasta / 'solucoes.csv'))
    if not serie:
        return
    t = [s[0] for s in serie]
    fig, ax = plt.subplots(figsize=(7.5, 3.8))
    for indice, (rotulo, cor) in enumerate((('na fila', SERIES[0]), ('em avaliação', SERIES[1]))):
        valores = [s[indice + 1] for s in serie]
        ax.plot(t, valores, color=cor, label=rotulo, drawstyle='steps-post')
        pico = max(range(len(valores)), key=valores.__getitem__)
        ax.annotate(f'{rotulo} (máx. {valores[pico]})', (t[pico], valores[pico]), xytext=(6, 4),
                    textcoords='offset points', fontsize=8.5, color=TINTA_SECUNDARIA)
    if resumo.get('capacidade'):
        ax.axhline(resumo['capacidade'], color=TINTA_SUAVE, linewidth=0.8, linestyle=(0, (3, 3)))
        ax.annotate(f'capacidade ({resumo["capacidade"]} boxes)', (1, resumo['capacidade']),
                    xycoords=('axes fraction', 'data'), xytext=(-2, 3), textcoords='offset points', ha='right',
                    fontsize=8, color=TINTA_SUAVE)
    inicio = min(s['enviado_em'] for s in metricas.carregar_solucoes(pasta / 'solucoes.csv'))
    for evento in resumo.get('eventos', []):
        x = evento['t'] - inicio
        ax.axvline(x, color=TINTA_SUAVE, linewidth=0.8)
        ax.annotate(evento['evento'].replace('_', ' '), (x, 1), xycoords=('data', 'axes fraction'), xytext=(3, -12),
                    textcoords='offset points', fontsize=8, color=TINTA_SECUNDARIA)
    ax.set_xlabel('tempo desde o primeiro envio (s)')
    ax.set_ylabel('soluções')
    ax.set_ylim(bottom=0)
    ax.set_title(f'Fila e avaliação — {resumo["run_id"]}')
    ax.legend(loc='upper right', fontsize=8.5)
    salvar(fig, pasta / 'ocupacao.png')


def degraus(pasta: Path, resumo: dict) -> list[dict]:
    """Recalcula pelo solucoes.csv, para valer também para resumos antigos."""
    return metricas.degraus_da_rampa(metricas.carregar_solucoes(pasta / 'solucoes.csv'), resumo['parametros'])


def grafico_rampa(plt, pasta: Path, resumo: dict) -> None:
    """
    Dois painéis com o mesmo eixo x (taxa ofertada por degrau): percentis do tempo total e vazão concluída. Acima da
    capacidade, a vazão se descola da diagonal e o tempo total dispara: é o joelho da curva.
    """
    lista = [d for d in degraus(pasta, resumo) if d['envios'] > 0]
    if len(lista) < 2:
        return
    x = [d['taxa_ofertada_por_min'] for d in lista]
    fig, (ax, ax_vazao) = plt.subplots(2, 1, figsize=(7.5, 6.4), sharex=True, gridspec_kw={'height_ratios': [3, 2]})
    for i, (chave, rotulo) in enumerate((('total_p50_ms', 'p50'), ('total_p95_ms', 'p95'), ('total_p99_ms', 'p99'))):
        y = [d[chave] / 1000 for d in lista]
        ax.plot(x, y, color=SERIES[i], marker='o', markeredgecolor=SUPERFICIE, markeredgewidth=1.5, label=rotulo)
        ax.annotate(rotulo, (x[-1], y[-1]), xytext=(6, 0), textcoords='offset points', va='center', fontsize=8.5,
                    color=TINTA_SECUNDARIA)
    ax.set_ylabel('tempo total (s)')
    ax.set_ylim(bottom=0)
    ax.set_title(f'Saturação na rampa — {resumo["run_id"]}')
    ax.legend(loc='upper left', fontsize=8.5)

    concluidas = [d['concluidas_por_min'] for d in lista]
    ax_vazao.plot(x, x, color=TINTA_SUAVE, linewidth=0.9, linestyle=(0, (3, 3)))
    ax_vazao.annotate('vazão = taxa ofertada', (x[-1], x[-1]), xytext=(-4, 4), textcoords='offset points',
                      ha='right', fontsize=8, color=TINTA_SUAVE)
    ax_vazao.plot(x, concluidas, color=SERIES[0], marker='o', markeredgecolor=SUPERFICIE, markeredgewidth=1.5)
    ax_vazao.annotate('vazão concluída', (x[-1], concluidas[-1]), xytext=(6, 0), textcoords='offset points',
                      va='center', fontsize=8.5, color=TINTA_SECUNDARIA)
    ax_vazao.set_ylabel('envios/min')
    ax_vazao.set_ylim(bottom=0)
    ax_vazao.set_xlabel('taxa ofertada no degrau (envios/min)')
    salvar(fig, pasta / 'rampa.png')


def grafico_escalabilidade(plt, envios: list[dict], destino: Path) -> None:
    familias: dict[tuple, dict[int, list[float]]] = {}
    for r in envios:
        if r.get('capacidade') and r.get('vazao_por_min'):
            familias.setdefault((r['cenario'], _parametro(r)), {}).setdefault(r['capacidade'], []).append(r['vazao_por_min'])
    familias = {k: v for k, v in familias.items() if len(v) >= 2}
    if not familias:
        return
    fig, ax = plt.subplots(figsize=(6.5, 4.2))
    for i, ((cenario, parametro), por_capacidade) in enumerate(list(familias.items())[:len(SERIES)]):
        c = sorted(por_capacidade)
        medias = [statistics.fmean(por_capacidade[k]) for k in c]
        desvios = [statistics.stdev(por_capacidade[k]) if len(por_capacidade[k]) > 1 else 0 for k in c]
        rotulo = f'{cenario} ({parametro})'
        ax.errorbar(c, medias, yerr=desvios, color=SERIES[i], marker='o', markeredgecolor=SUPERFICIE,
                    markeredgewidth=1.5, capsize=3, label=rotulo)
        ideal = [medias[0] * k / c[0] for k in c]
        ax.plot(c, ideal, color=TINTA_SUAVE, linewidth=0.9, linestyle=(0, (3, 3)))
        if i == 0:
            ax.annotate('ideal (linear)', (c[-1], ideal[-1]), xytext=(-4, 4), textcoords='offset points', ha='right',
                        fontsize=8, color=TINTA_SUAVE)
        ax.annotate(rotulo, (c[-1], medias[-1]), xytext=(6, -2), textcoords='offset points', fontsize=8.5,
                    color=TINTA_SECUNDARIA)
    ax.set_xlabel('capacidade (réplicas × boxes)')
    ax.set_ylabel('vazão (envios avaliados/min)')
    ax.set_xticks(sorted({k for v in familias.values() for k in v}))
    ax.set_ylim(bottom=0)
    ax.set_title('Escalabilidade da avaliação')
    if len(familias) >= 2:
        ax.legend(loc='upper left', fontsize=8.5)
    salvar(fig, destino)


def grafico_fases(plt, grupos: dict[str, list[dict]], destino: Path) -> None:
    """Média de cada fase por grupo (as médias somam o tempo total médio, ao contrário dos percentis)."""
    nomes = list(grupos)
    if not nomes:
        return
    fases = (('publicacao', 'publicação'), ('fila', 'fila'), ('avaliacao', 'avaliação'))
    fig, ax = plt.subplots(figsize=(7.5, 0.9 + 0.45 * len(nomes)))
    esquerda = [0.0] * len(nomes)
    for i, (fase, rotulo) in enumerate(fases):
        valores = [statistics.fmean([r['fases_ms'][fase].get('media') or 0 for r in grupos[n]]) / 1000 for n in nomes]
        ax.barh(nomes, valores, left=esquerda, color=SERIES[i], edgecolor=SUPERFICIE, linewidth=2, height=0.6,
                label=rotulo)
        esquerda = [a + b for a, b in zip(esquerda, valores)]
    for y, total in enumerate(esquerda):
        ax.annotate(f'{total:.1f} s', (total, y), xytext=(4, 0), textcoords='offset points', va='center',
                    fontsize=8.5, color=TINTA_SECUNDARIA)
    ax.invert_yaxis()
    ax.grid(axis='y', visible=False)
    ax.set_xlabel('tempo médio (s)')
    ax.set_title('Onde o tempo é gasto (média por fase)')
    ax.legend(loc='upper center', bbox_to_anchor=(0.5, -0.18 if len(nomes) > 3 else -0.35), ncol=3, fontsize=8.5)
    salvar(fig, destino)


def gerar_graficos(execucoes: list[tuple[Path, dict]]) -> None:
    try:
        import matplotlib
        matplotlib.use('Agg')
        import matplotlib.pyplot as plt
    except ImportError:
        print('matplotlib não instalado: só as tabelas foram geradas (pip install matplotlib)')
        return
    estilo(plt)
    destino = RESULTADOS / 'graficos'
    destino.mkdir(exist_ok=True)
    envios = [(p, r) for p, r in execucoes if r['cenario'] != 'leitura' and (p / 'solucoes.csv').exists()]
    if not envios:
        return
    pastas_por_grupo: dict[str, list[Path]] = {}
    for pasta, r in envios:
        pastas_por_grupo.setdefault(r['grupo'], []).append(pasta)
        grafico_ocupacao(plt, pasta, r)
        if r['cenario'] == 'rampa':
            grafico_rampa(plt, pasta, r)
    grafico_cdf(plt, pastas_por_grupo, destino / 'cdf-tempo-total.png')
    grafico_fases(plt, agrupar([r for _, r in envios]), destino / 'fases.png')
    grafico_escalabilidade(plt, [r for _, r in envios], destino / 'escalabilidade.png')
    plt.close('all')


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--grupos', help='expressão regular para escolher os grupos (ex.: "pico-r.*")')
    parser.add_argument('--sem-graficos', action='store_true')
    args = parser.parse_args()
    execucoes = carregar(args.grupos)
    if not execucoes:
        print(f'Nenhuma execução em {RESULTADOS}')
        return
    escrever_relatorio(execucoes)
    if not args.sem_graficos:
        gerar_graficos(execucoes)


if __name__ == '__main__':
    main()
