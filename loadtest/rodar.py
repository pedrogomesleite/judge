#!/usr/bin/env python3
"""
Orquestra os testes de carga do Juizin (detalhes em loadtest/README.md).

Para cada combinação de réplicas do runner × boxes × repetição: ajusta os runners, espera a fila esvaziar, aquece,
roda o k6, amostra a fila do RabbitMQ e o uso de CPU/memória dos containers, espera todas as soluções terminarem e
calcula as métricas por fase a partir dos instantes gravados no banco. Tudo fica em loadtest/resultados/<execução>/.

Precisa só do Docker (o mesmo do compose.yaml, com o ambiente no ar) e do Python 3.10+, sem bibliotecas extras.
Exemplos (na raiz do repositório; no Windows, dentro do WSL onde está o Docker):
    python3 loadtest/rodar.py baseline --envios 50
    python3 loadtest/rodar.py carga --taxa 40 --duracao 5m --repeticoes 3
    python3 loadtest/rodar.py rampa --taxa-inicial 10 --taxa-final 150 --degraus 8
    python3 loadtest/rodar.py pico --envios 200 --runners 1,2,4 --boxes 2 --repeticoes 3
    python3 loadtest/rodar.py carga --taxa 40 --duracao 4m --derrubar-runner 60
    python3 loadtest/rodar.py leitura --taxa 100 --duracao 2m
"""
from __future__ import annotations

import argparse
import base64
import csv
import datetime as dt
import json
import os
import platform
import re
import shutil
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

import metricas

RAIZ = Path(__file__).resolve().parent.parent
LOADTEST = RAIZ / 'loadtest'
RESULTADOS = LOADTEST / 'resultados'
COMPOSE = ['docker', 'compose', '--project-directory', str(RAIZ),
           '-f', str(RAIZ / 'compose.yaml'), '-f', str(LOADTEST / 'compose.k6.yaml')]
FILA_TRABALHO = 'submissions.cpp'
MIX_PADRAO = 'aceita:60,errada:20,pesada:10,compilacao:5,tle:5'
PROGRESSO_K6 = re.compile(r'^\S+\s+[✓✗↓]?\s*\[\s*\d+%\s*\]')


def log(mensagem: str) -> None:
    print(f'[{dt.datetime.now():%H:%M:%S}] {mensagem}', flush=True)


def rodar(cmd: list[str], **kwargs) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, capture_output=True, text=True, encoding='utf-8', **kwargs)


# --- Infraestrutura ---------------------------------------------------------------------------------------------------

class Rabbit:
    """API HTTP do management do RabbitMQ (as estatísticas das filas são atualizadas a cada 5 s)."""

    def __init__(self, url: str, usuario: str, senha: str):
        self.url = url.rstrip('/')
        self.autorizacao = 'Basic ' + base64.b64encode(f'{usuario}:{senha}'.encode()).decode()

    def fila(self, nome: str) -> dict | None:
        requisicao = urllib.request.Request(f'{self.url}/api/queues/%2F/{urllib.parse.quote(nome, safe="")}',
                                            headers={'Authorization': self.autorizacao})
        try:
            with urllib.request.urlopen(requisicao, timeout=5) as resposta:
                return json.load(resposta)
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            raise


def psql(sql: str, **variaveis: str) -> str:
    cmd = COMPOSE + ['exec', '-T', 'postgres', 'psql', '-U', 'postgres', '-d', 'juizin', '-X', '-q',
                     '-v', 'ON_ERROR_STOP=1', '--csv']
    for nome, valor in variaveis.items():
        cmd += ['-v', f'{nome}={valor}']
    resultado = rodar(cmd + ['-f', '-'], input=sql)
    if resultado.returncode != 0:
        raise RuntimeError(f'psql falhou: {resultado.stderr.strip()}')
    return resultado.stdout


def pendentes(login: str) -> tuple[int, int]:
    saida = psql("""
        SELECT count(*) FILTER (WHERE s.finalizada_em IS NULL), count(*)
        FROM solucao s JOIN usuario u ON u.id = s.autor_id WHERE u.login = :'login';
        """, login=login)
    linha = saida.strip().splitlines()[-1].split(',')
    return int(linha[0]), int(linha[1])


def containers_do_projeto(servico: str | None = None) -> list[str]:
    resultado = rodar(COMPOSE + ['ps', '-q'] + ([servico] if servico else []))
    return resultado.stdout.split()


def escalar_runners(rabbit: Rabbit, runners: int, boxes: int, timeout_s: int = 600) -> None:
    log(f'Ajustando o runner-cpp: {runners} réplica(s) × {boxes} box(es)')
    resultado = rodar(['docker', 'compose', '--project-directory', str(RAIZ), '-f', str(RAIZ / 'compose.yaml'),
                       'up', '-d', '--scale', f'runner-cpp={runners}', 'runner-cpp'],
                      env={**os.environ, 'JUIZIN_RUNNER_BOXES': str(boxes)})
    if resultado.returncode != 0:
        raise RuntimeError(f'docker compose up falhou:\n{resultado.stderr}')
    esperado = runners * boxes
    limite = time.time() + timeout_s
    while time.time() < limite:
        fila = rabbit.fila(FILA_TRABALHO)
        if fila and fila.get('consumers') == esperado:
            log(f'{esperado} consumidor(es) em {FILA_TRABALHO}')
            return
        time.sleep(3)
    raise RuntimeError(f'{FILA_TRABALHO} não chegou a {esperado} consumidores em {timeout_s}s '
                       f'(veja docker compose logs runner-cpp)')


def aguardar_fila_vazia(rabbit: Rabbit, timeout_s: int) -> None:
    limite = time.time() + timeout_s
    while time.time() < limite:
        fila = rabbit.fila(FILA_TRABALHO)
        if fila is None or fila.get('messages_ready', 0) + fila.get('messages_unacknowledged', 0) == 0:
            return
        log(f'Aguardando a fila esvaziar ({fila.get("messages_ready")} prontas, '
            f'{fila.get("messages_unacknowledged")} em execução)')
        time.sleep(5)
    raise RuntimeError(f'{FILA_TRABALHO} não esvaziou em {timeout_s}s: a execução anterior contaminaria esta')


def k6(script: str, env: dict[str, str], saida: Path | None, api_url: str | None) -> int:
    cmd = COMPOSE + ['run', '--rm', '-T', '--no-deps']
    if hasattr(os, 'getuid'):
        # O k6 grava o resumo no volume loadtest/resultados com o usuário de quem roda o script
        cmd += ['--user', f'{os.getuid()}:{os.getgid()}']
    cmd += ['k6', 'run']
    # Variáveis como opção do k6 (-e), e não do container: assim chegam ao __ENV em qualquer versão
    for nome, valor in {**env, 'API_URL': api_url or 'http://api:8080'}.items():
        cmd += ['-e', f'{nome}={valor}']
    cmd.append(f'/scripts/{script}')
    arquivo = saida.open('w', encoding='utf-8') if saida else None
    try:
        processo = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding='utf-8')
        # Sem TTY, o k6 imprime o progresso a cada segundo: o arquivo guarda tudo, o console mostra a cada 15 s
        brancas, ultimo_progresso, mostrar = 0, 0.0, False
        for linha in processo.stdout:
            if arquivo:
                arquivo.write(linha)
            if not linha.strip():
                brancas += 1
                continue
            if linha.startswith('running ('):
                mostrar = time.time() - ultimo_progresso >= 15
                ultimo_progresso = time.time() if mostrar else ultimo_progresso
            elif not PROGRESSO_K6.match(linha):
                print('\n' * brancas + linha, end='', flush=True)
                brancas = 0
                continue
            brancas = 0
            if mostrar:
                print(linha, end='', flush=True)
        return processo.wait()
    finally:
        if arquivo:
            arquivo.close()


class Amostrador(threading.Thread):
    """Chama `coletar` a cada `intervalo_s` e grava as linhas em CSV, com o instante (epoch em s) na primeira coluna."""

    def __init__(self, arquivo: Path, campos: list[str], coletar, intervalo_s: float):
        super().__init__(daemon=True)
        self.arquivo, self.campos, self.coletar, self.intervalo_s = arquivo, campos, coletar, intervalo_s
        self.parar = threading.Event()

    def run(self) -> None:
        with self.arquivo.open('w', encoding='utf-8', newline='') as f:
            escritor = csv.writer(f)
            escritor.writerow(['t'] + self.campos)
            while not self.parar.is_set():
                try:
                    agora = time.time()
                    for linha in self.coletar():
                        escritor.writerow([f'{agora:.3f}'] + linha)
                    f.flush()
                except Exception as e:  # noqa: BLE001 - uma amostra perdida não deve derrubar o teste
                    log(f'Amostra perdida ({self.arquivo.name}): {e}')
                self.parar.wait(self.intervalo_s)


def amostrar_fila(rabbit: Rabbit):
    def coletar():
        linhas = []
        for nome in (FILA_TRABALHO, 'submissions.results', 'submissions.dlq'):
            fila = rabbit.fila(nome) or {}
            linhas.append([nome, fila.get('messages_ready', ''), fila.get('messages_unacknowledged', ''),
                           fila.get('consumers', '')])
        return linhas
    return coletar


def _megabytes(texto: str) -> float:
    numero, unidade = re.match(r'([\d.]+)\s*([A-Za-z]+)', texto).groups()
    fator = {'B': 1 / 2 ** 20, 'KiB': 1 / 1024, 'kB': 1 / 1024, 'MiB': 1, 'MB': 1, 'GiB': 1024, 'GB': 1024}
    return float(numero) * fator.get(unidade, 1)


def projeto_compose() -> str | None:
    ids = containers_do_projeto()
    if not ids:
        return None
    resultado = rodar(['docker', 'inspect', '--format', '{{index .Config.Labels "com.docker.compose.project"}}', ids[0]])
    return resultado.stdout.strip() or None


def amostrar_recursos(projeto: str | None):
    def coletar():
        # Busca a cada amostra pelo label do compose, para incluir o container do k6 (docker compose run)
        ids = rodar(['docker', 'ps', '-q', '--filter', f'label=com.docker.compose.project={projeto}']).stdout.split() \
            if projeto else []
        if not ids:  # sem ids, o docker stats mostraria todos os containers da máquina
            time.sleep(2)
            return []
        # docker stats --no-stream leva ~1-2 s, porque mede a CPU entre duas leituras
        resultado = rodar(['docker', 'stats', '--no-stream', '--format', '{{json .}}', *ids])
        linhas = []
        for texto in resultado.stdout.splitlines():
            item = json.loads(texto)
            linhas.append([item['Name'], item['CPUPerc'].rstrip('%'), f'{_megabytes(item["MemUsage"].split("/")[0]):.1f}'])
        return linhas
    return coletar


def derrubar_runner(apos_s: float, retorno_s: float, eventos: list, cancelar: threading.Event) -> None:
    """Simula a queda de uma réplica (SIGKILL) no meio da carga e a sobe de novo depois de `retorno_s`."""
    if cancelar.wait(apos_s):
        return
    ids = containers_do_projeto('runner-cpp')
    if not ids:
        log('Nenhum runner para derrubar')
        return
    alvo = ids[0]
    rodar(['docker', 'kill', alvo])
    eventos.append({'evento': 'runner_derrubado', 't': time.time(), 'container': alvo[:12]})
    log(f'Runner {alvo[:12]} derrubado (SIGKILL)')
    cancelar.wait(retorno_s)
    rodar(['docker', 'start', alvo])
    eventos.append({'evento': 'runner_reiniciado', 't': time.time(), 'container': alvo[:12]})
    log(f'Runner {alvo[:12]} reiniciado')


# --- Metadados --------------------------------------------------------------------------------------------------------

def metadados_do_ambiente() -> dict:
    git = rodar(['git', '-C', str(RAIZ), 'rev-parse', '--short', 'HEAD']).stdout.strip()
    alterado = bool(rodar(['git', '-C', str(RAIZ), 'status', '--porcelain']).stdout.strip())
    docker = rodar(['docker', 'info', '--format',
                    '{"versao":"{{.ServerVersion}}","cgroup":"{{.CgroupVersion}}","cpus":{{.NCPU}},"memoria":{{.MemTotal}}}'])
    try:
        info_docker = json.loads(docker.stdout)
        info_docker['memoria_gb'] = round(info_docker.pop('memoria') / 2 ** 30, 1)
    except (json.JSONDecodeError, KeyError):
        info_docker = {'erro': docker.stderr.strip()}
    cpu = ''
    if Path('/proc/cpuinfo').exists():
        nomes = re.findall(r'model name\s*:\s*(.+)', Path('/proc/cpuinfo').read_text(encoding='utf-8', errors='ignore'))
        cpu = nomes[0] if nomes else ''
    return {
        'git': git + ('-alterado' if alterado else ''),
        'host': {'so': platform.platform(), 'cpu': cpu, 'cpus': os.cpu_count()},
        'docker': info_docker,
        'k6': 'grafana/k6:1.3.0',
    }


# --- Uma execução -----------------------------------------------------------------------------------------------------

def identificador(args, runners: int | None, boxes: int | None, repeticao: int) -> tuple[str, str]:
    partes = [args.cenario]
    if args.rotulo:
        partes.append(re.sub(r'[^a-z0-9]+', '-', args.rotulo.lower()).strip('-')[:12])
    if runners:
        partes.append(f'r{runners}b{boxes}')
    grupo = '-'.join(partes)
    return f'{dt.datetime.now():%Y%m%d-%H%M%S}-{grupo}-{repeticao}', grupo


def env_do_cenario(args) -> dict[str, str]:
    env = {'CENARIO': args.cenario, 'MIX': args.mix, 'VUS_MAX': str(args.vus_max),
           'ESPERA_FINAL': f'{args.espera_drenagem}s', 'TIMEOUT_VEREDITO': f'{args.espera_drenagem}s'}
    if args.cenario in ('baseline', 'pico'):
        env['ENVIOS'] = str(args.envios)
    if args.cenario == 'carga':
        env.update(TAXA=str(args.taxa), DURACAO=args.duracao)
    if args.cenario == 'rampa':
        env.update(TAXA_INICIAL=str(args.taxa_inicial), TAXA_FINAL=str(args.taxa_final),
                   DEGRAUS=str(args.degraus), DURACAO_DEGRAU=args.duracao_degrau)
    return env


def executar(args, rabbit: Rabbit, runners: int | None, boxes: int | None, repeticao: int, ambiente: dict) -> dict:
    run_id, grupo = identificador(args, runners, boxes, repeticao)
    pasta = RESULTADOS / run_id
    pasta.mkdir(parents=True, exist_ok=True)
    login = f'lt-{run_id}'
    log(f'=== {run_id} ===')

    consumidores = (rabbit.fila(FILA_TRABALHO) or {}).get('consumers')
    replicas = len(containers_do_projeto('runner-cpp'))
    capacidade = runners * boxes if runners else consumidores

    aguardar_fila_vazia(rabbit, args.espera_drenagem)
    if args.aquecimento > 0:
        log(f'Aquecimento: {args.aquecimento} envio(s) em sequência (descartados)')
        codigo = k6('submissoes.js', {'CENARIO': 'baseline', 'ENVIOS': str(args.aquecimento), 'MIX': args.mix,
                                      'RUN_ID': f'{run_id}-aq'}, pasta / 'aquecimento.txt', args.api_url)
        if codigo != 0:
            raise RuntimeError(f'Aquecimento falhou (código {codigo}); veja {pasta / "aquecimento.txt"}')

    amostradores = [
        Amostrador(pasta / 'fila.csv', ['fila', 'prontas', 'em_execucao', 'consumidores'], amostrar_fila(rabbit), 2),
        Amostrador(pasta / 'recursos.csv', ['container', 'cpu_pct', 'mem_mb'], amostrar_recursos(projeto_compose()), 0),
    ]
    eventos: list[dict] = []
    cancelar = threading.Event()
    falha = None
    if args.derrubar_runner:
        falha = threading.Thread(target=derrubar_runner, daemon=True,
                                 args=(args.derrubar_runner, args.retorno_runner, eventos, cancelar))

    inicio = time.time()
    for amostrador in amostradores:
        amostrador.start()
    if falha:
        falha.start()

    env = {**env_do_cenario(args), 'RUN_ID': run_id, 'RESUMO_JSON': f'/resultados/{run_id}/k6-resumo.json'}
    codigo = k6('submissoes.js', env, pasta / 'k6-saida.txt', args.api_url)
    fim_k6 = time.time()
    if codigo not in (0, 99):  # 99 = thresholds; aqui eles só expõem submétricas
        cancelar.set()
        for amostrador in amostradores:
            amostrador.parar.set()
        raise RuntimeError(f'k6 terminou com código {codigo}; veja {pasta / "k6-saida.txt"}')

    # O k6 já espera os vereditos pelo SSE; isto cobre envios que ele abandonou (timeout) ou o pico de reentregas
    limite = time.time() + args.espera_drenagem
    while True:
        faltam, total = pendentes(login)
        if faltam == 0 or time.time() > limite:
            break
        log(f'Aguardando {faltam} de {total} solução(ões) terminarem')
        time.sleep(3)
    time.sleep(3)
    cancelar.set()
    for amostrador in amostradores:
        amostrador.parar.set()
        amostrador.join(timeout=10)

    (pasta / 'solucoes.csv').write_text(
        psql((LOADTEST / 'sql' / 'solucoes-do-teste.sql').read_text(encoding='utf-8'), login=login),
        encoding='utf-8')
    solucoes = metricas.carregar_solucoes(pasta / 'solucoes.csv')

    resumo = {
        'run_id': run_id, 'grupo': grupo, 'cenario': args.cenario, 'repeticao': repeticao, 'login': login,
        'runners': runners or replicas, 'boxes': boxes or (consumidores // replicas if replicas and consumidores else None),
        'capacidade': capacidade, 'mistura': args.mix, 'parametros': env_do_cenario(args),
        'inicio': dt.datetime.fromtimestamp(inicio).isoformat(timespec='seconds'),
        'duracao_k6_s': round(fim_k6 - inicio, 1),
        'eventos': eventos,
        **metricas.resumir_solucoes(solucoes, capacidade, args.janela),
    }
    if args.cenario == 'rampa':
        resumo['degraus'] = metricas.degraus_da_rampa(solucoes, resumo['parametros'])
    arquivo_k6 = pasta / 'k6-resumo.json'
    if arquivo_k6.exists():
        resumo_k6 = json.loads(arquivo_k6.read_text(encoding='utf-8'))
        resumo['cliente'] = {
            'envio_ms': metricas.tendencia_k6(resumo_k6, 'juizin_envio_ms'),
            'tempo_total_ms': metricas.tendencia_k6(resumo_k6, 'juizin_tempo_total_ms'),
            'sem_veredito': metricas.valor_k6(resumo_k6, 'juizin_sem_veredito', 'count') or 0,
            'iteracoes_descartadas': metricas.valor_k6(resumo_k6, 'dropped_iterations', 'count') or 0,
        }
    resumo['recursos'] = resumir_recursos(pasta / 'recursos.csv')

    (pasta / 'resumo.json').write_text(json.dumps(resumo, indent=2, ensure_ascii=False), encoding='utf-8')
    (pasta / 'meta.json').write_text(json.dumps({**ambiente, 'argumentos': vars(args)}, indent=2, ensure_ascii=False,
                                                default=str), encoding='utf-8')
    imprimir(resumo)
    return resumo


def resumir_recursos(arquivo: Path) -> dict:
    if not arquivo.exists():
        return {}
    por_container: dict[str, dict[str, list[float]]] = {}
    with arquivo.open(encoding='utf-8', newline='') as f:
        for linha in csv.DictReader(f):
            dados = por_container.setdefault(linha['container'], {'cpu': [], 'mem': []})
            dados['cpu'].append(float(linha['cpu_pct']))
            dados['mem'].append(float(linha['mem_mb']))
    return {nome: {'cpu_medio_pct': sum(d['cpu']) / len(d['cpu']), 'cpu_max_pct': max(d['cpu']),
                   'mem_max_mb': max(d['mem']), 'amostras': len(d['cpu'])}
            for nome, d in sorted(por_container.items())}


def _ms(valor) -> str:
    return '-' if valor is None else f'{valor:.0f}'


def imprimir(resumo: dict) -> None:
    correto = resumo.get('veredito_correto')
    print()
    print(f'Envios: {resumo["envios"]} | finalizadas: {resumo["finalizadas"]} | FALHA: {resumo["falhas"]} | '
          f'veredito esperado: {"-" if correto is None else f"{correto:.2%}"}')
    if resumo.get('vazao_por_min') is not None:
        utilizacao = resumo.get('utilizacao_boxes')
        print(f'Vazão: {resumo["vazao_por_min"]:.1f} envios/min | makespan {resumo["makespan_s"]:.0f} s | '
              f'drenagem {resumo["drenagem_s"]:.0f} s | pico na fila {resumo["pico_em_fila"]} | '
              f'utilização dos boxes {"-" if utilizacao is None else f"{utilizacao:.0%}"}')
    colunas = ['n', 'media', 'p50', 'p90', 'p95', 'p99', 'p99_9', 'max']
    print(f'{"(ms)":<30}' + ''.join(f'{c:>9}' for c in colunas))
    linhas = [(f'servidor: {fase}', est) for fase, est in resumo['fases_ms'].items()]
    for chave, rotulo in (('envio_ms', 'cliente: envio (202)'), ('tempo_total_ms', 'cliente: total (SSE)')):
        if resumo.get('cliente', {}).get(chave):
            linhas.append((rotulo, resumo['cliente'][chave]))
    for rotulo, est in linhas:
        print(f'{rotulo:<30}' + ''.join(f'{(str(est.get(c, "-")) if c == "n" else _ms(est.get(c))):>9}'
                                        for c in colunas))
    for d in resumo.get('degraus', []):
        print(f'  degrau {d["degrau"]}: ofertada {d["taxa_ofertada_por_min"]}/min, concluídas '
              f'{d["concluidas_por_min"]:.1f}/min, total p50/p95/p99 = {_ms(d["total_p50_ms"])}/'
              f'{_ms(d["total_p95_ms"])}/{_ms(d["total_p99_ms"])} ms')
    for item in resumo.get('vereditos_inesperados', []):
        print(f'  veredito inesperado: tipo={item["tipo"]} → {item["veredito"]} ({item["n"]}x)')
    print()


# --- Cenário de leitura (sem fila) ------------------------------------------------------------------------------------

def executar_leitura(args, ambiente: dict) -> None:
    run_id, grupo = identificador(args, None, None, 1)
    pasta = RESULTADOS / run_id
    pasta.mkdir(parents=True, exist_ok=True)
    log(f'=== {run_id} ===')
    amostrador = Amostrador(pasta / 'recursos.csv', ['container', 'cpu_pct', 'mem_mb'],
                            amostrar_recursos(projeto_compose()), 0)
    amostrador.start()
    codigo = k6('leitura.js', {'TAXA': str(args.taxa), 'DURACAO': args.duracao, 'VUS_MAX': str(args.vus_max),
                               'RESUMO_JSON': f'/resultados/{run_id}/k6-resumo.json'},
                pasta / 'k6-saida.txt', args.api_url)
    amostrador.parar.set()
    amostrador.join(timeout=10)
    if codigo not in (0, 99):
        raise RuntimeError(f'k6 terminou com código {codigo}; veja {pasta / "k6-saida.txt"}')
    resumo_k6 = json.loads((pasta / 'k6-resumo.json').read_text(encoding='utf-8'))
    endpoints = ['atividades', 'atividade', 'casos-publicos', 'linguagens']
    resumo = {
        'run_id': run_id, 'grupo': grupo, 'cenario': 'leitura', 'parametros': {'TAXA': args.taxa, 'DURACAO': args.duracao},
        'http_req_duration': metricas.tendencia_k6(resumo_k6, 'http_req_duration'),
        'por_endpoint': {e: metricas.tendencia_k6(resumo_k6, f'http_req_duration{{endpoint:{e}}}') for e in endpoints},
        'taxa_de_erro': metricas.valor_k6(resumo_k6, 'http_req_failed', 'rate'),
        'requisicoes_por_s': metricas.valor_k6(resumo_k6, 'http_reqs', 'rate'),
        'iteracoes_descartadas': metricas.valor_k6(resumo_k6, 'dropped_iterations', 'count') or 0,
        'recursos': resumir_recursos(pasta / 'recursos.csv'),
    }
    (pasta / 'resumo.json').write_text(json.dumps(resumo, indent=2, ensure_ascii=False), encoding='utf-8')
    (pasta / 'meta.json').write_text(json.dumps({**ambiente, 'argumentos': vars(args)}, indent=2, ensure_ascii=False,
                                                default=str), encoding='utf-8')


# --- CLI --------------------------------------------------------------------------------------------------------------

def lista_de_inteiros(texto: str) -> list[int]:
    return [int(parte) for parte in texto.split(',') if parte.strip()]


def argumentos() -> argparse.Namespace:
    comum = argparse.ArgumentParser(add_help=False)
    comum.add_argument('--rotulo', help='nome curto que entra no identificador da execução')
    comum.add_argument('--api-url', help='URL da API vista pelo k6 (padrão: http://api:8080, na rede do compose)')
    comum.add_argument('--rabbit-url', default='http://localhost:15672', help='management do RabbitMQ')
    comum.add_argument('--rabbit-usuario', default='juizin')
    comum.add_argument('--rabbit-senha', default='juizin')
    comum.add_argument('--vus-max', type=int, default=500, help='VUs máximos do k6 (padrão: 500)')

    envio = argparse.ArgumentParser(add_help=False, parents=[comum])
    envio.add_argument('--mix', default=MIX_PADRAO, help=f'mistura de soluções (padrão: {MIX_PADRAO})')
    envio.add_argument('--runners', type=lista_de_inteiros,
                       help='réplicas do runner-cpp; lista para varrer, ex.: 1,2,4 (padrão: não altera)')
    envio.add_argument('--boxes', type=lista_de_inteiros, default=None,
                       help='boxes por réplica; lista para varrer, ex.: 1,2,4 (padrão: 2 se --runners for usado)')
    envio.add_argument('--repeticoes', type=int, default=1, help='repetições de cada configuração (padrão: 1)')
    envio.add_argument('--aquecimento', type=int, default=10,
                       help='envios em sequência antes de medir, descartados (padrão: 10; 0 desliga)')
    envio.add_argument('--espera-drenagem', type=int, default=900,
                       help='quanto esperar as soluções terminarem e a fila esvaziar, em s (padrão: 900)')
    envio.add_argument('--janela', type=float, default=30, help='largura das janelas de tempo, em s (padrão: 30)')
    envio.add_argument('--derrubar-runner', type=float, metavar='S',
                       help='derruba uma réplica do runner S segundos depois do início (teste de resiliência)')
    envio.add_argument('--retorno-runner', type=float, default=15, metavar='S',
                       help='quanto tempo a réplica derrubada fica fora (padrão: 15 s)')

    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest='cenario', required=True)
    p = sub.add_parser('baseline', parents=[envio], help='um envio por vez: tempo de serviço sem fila')
    p.add_argument('--envios', type=int, default=50)
    p = sub.add_parser('carga', parents=[envio], help='taxa constante: percentis em regime estável')
    p.add_argument('--taxa', type=int, default=30, help='envios por minuto (padrão: 30)')
    p.add_argument('--duracao', default='5m')
    p = sub.add_parser('rampa', parents=[envio], help='taxa crescente em degraus: ponto de saturação')
    p.add_argument('--taxa-inicial', type=int, default=10, help='envios por minuto (padrão: 10)')
    p.add_argument('--taxa-final', type=int, default=120, help='envios por minuto (padrão: 120)')
    p.add_argument('--degraus', type=int, default=6)
    p.add_argument('--duracao-degrau', default='2m')
    p = sub.add_parser('pico', parents=[envio], help='todos os envios de uma vez: fila, drenagem e vazão máxima')
    p.add_argument('--envios', type=int, default=100)
    p = sub.add_parser('leitura', parents=[comum], help='catálogo público (síncrono), para comparação')
    p.add_argument('--taxa', type=int, default=50, help='requisições por segundo (padrão: 50)')
    p.add_argument('--duracao', default='2m')
    return parser.parse_args()


def main() -> int:
    args = argumentos()
    if not shutil.which('docker'):
        sys.exit('docker não encontrado no PATH. No Windows, rode dentro do WSL, onde está o Docker '
                 '(ex.: wsl -d Ubuntu -- python3 loadtest/rodar.py ...)')
    RESULTADOS.mkdir(parents=True, exist_ok=True)
    ambiente = metadados_do_ambiente()
    if ambiente['docker'].get('cgroup') == '1':
        log('Aviso: o Docker está em cgroup v1; o runner-cpp exige cgroup v2 (veja runner-cpp/README.md)')

    if args.cenario == 'leitura':
        executar_leitura(args, ambiente)
        return 0

    rabbit = Rabbit(args.rabbit_url, args.rabbit_usuario, args.rabbit_senha)
    combinacoes = [(None, None)]
    if args.runners:
        combinacoes = [(r, b) for r in args.runners for b in (args.boxes or [2])]
    elif args.boxes:
        sys.exit('--boxes exige --runners')

    resumos = []
    for runners, boxes in combinacoes:
        if runners:
            escalar_runners(rabbit, runners, boxes)
        for repeticao in range(1, args.repeticoes + 1):
            resumos.append(executar(args, rabbit, runners, boxes, repeticao, ambiente))

    if len(resumos) > 1:
        print(f'{"execução":<48}{"p50":>8}{"p95":>8}{"p99":>8}{"vazão/min":>11}{"correto":>9}')
        for r in resumos:
            total = r['fases_ms']['total']
            vazao = r.get('vazao_por_min')
            print(f'{r["run_id"]:<48}{_ms(total.get("p50")):>8}{_ms(total.get("p95")):>8}{_ms(total.get("p99")):>8}'
                  f'{"-" if vazao is None else f"{vazao:.1f}":>11}{(r.get("veredito_correto") or 0):>9.2%}')
    log('Resultados em loadtest/resultados/. Relatório e gráficos: python3 loadtest/relatorio.py')
    return 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        sys.exit(130)
    except RuntimeError as e:
        log(f'Erro: {e}')
        sys.exit(1)
