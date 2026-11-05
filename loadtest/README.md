# Testes de carga do Juizin

Automação dos testes de desempenho do artigo: mede o tempo do envio ao veredito (p50, p95, p99), a vazão e onde o
tempo é gasto (publicação, espera na fila, avaliação).

> **Ainda não executados.** Os testes serão rodados no outro computador (ver [Testes futuros](#testes-futuros)).

## Como rodar

1. **Suba o ambiente recompilando a API** (os novos instantes da `Solucao` estão no código dela):
   ```sh
   docker compose up -d --build
   ```
2. **Abra um terminal onde está o Docker**, na raiz do repositório (no Windows, dentro do WSL):
   ```sh
   wsl -d Ubuntu
   cd /mnt/c/Users/<usuário>/IdeaProjects/judge
   ```
3. **Rode os testes** (Python 3.10+, sem bibliotecas extras; taxas em envios por minuto):
   - Linha de base: `python3 loadtest/rodar.py baseline --envios 50`
   - Rampa: `python3 loadtest/rodar.py rampa --taxa-inicial 10 --taxa-final 150 --degraus 8 --duracao-degrau 2m`
   - Carga constante, a ~50% e ~80% da capacidade encontrada na rampa:
     `python3 loadtest/rodar.py carga --taxa 40 --duracao 10m --repeticoes 3 --rotulo 50pct`
   - Pico: `python3 loadtest/rodar.py pico --envios 300 --repeticoes 3`
   - Leitura: `python3 loadtest/rodar.py leitura --taxa 100 --duracao 3m`
   - Todas as opções de um teste: `python3 loadtest/rodar.py <teste> --help`
4. **Gere as tabelas e os gráficos** (sem o matplotlib, saem só as tabelas):
   - No Windows: `python -m pip install matplotlib` e `python loadtest\relatorio.py`
   - No WSL (Ubuntu): `sudo apt install python3-matplotlib` e `python3 loadtest/relatorio.py`
5. **Veja os resultados** em `loadtest/resultados/`: uma pasta por execução, mais `relatorio.md`, `relatorio.csv` e
   `graficos/`.
6. **Limpe os dados de teste do banco** ao terminar (opcional):
   ```sh
   docker compose exec -T postgres psql -U postgres -d juizin -f - < loadtest/sql/limpar.sql
   ```

## O que cada teste mede

Nos testes de envio, cada solução é acompanhada até o veredito, e o tempo é dividido em publicação, espera na fila e
avaliação. A mistura padrão imita uma turma: a maioria das soluções está certa, algumas erram e poucas estouram o tempo.

- **Linha de base (`baseline`):** envia uma solução por vez, sem concorrência. Mede quanto custa avaliar uma solução
  quando não há fila. É a referência para comparar os outros testes.
- **Carga constante (`carga`):** envia soluções num ritmo fixo durante alguns minutos. Mede os percentis com o sistema
  estável, abaixo do limite.
- **Rampa (`rampa`):** aumenta o ritmo de envios em degraus. Mostra em que ponto o sistema satura: a vazão para de
  crescer e o tempo de resposta dispara.
- **Pico (`pico`):** envia todas as soluções de uma vez, como no fim de uma prova. Mede o tamanho máximo da fila,
  quanto tempo ela leva para esvaziar e a vazão máxima.
- **Leitura (`leitura`):** consulta o catálogo público, sem fila nem runner. Mede a latência da parte síncrona da API,
  para comparar com o fluxo assíncrono do envio.

## Testes futuros

Pendentes, para rodar no outro computador.

- [ ] **Escalabilidade:** a vazão cresce na proporção do número de runners e de boxes?
  - Horizontal (réplicas): `python3 loadtest/rodar.py pico --envios 300 --runners 1,2,4 --boxes 2 --repeticoes 3`
  - Vertical (boxes por réplica):
    `python3 loadtest/rodar.py pico --envios 300 --runners 1 --boxes 1,2,4,8 --repeticoes 3 --espera-drenagem 1800`
  - Onde ver: tabela "Escalabilidade" do `relatorio.md` e `graficos/escalabilidade.png`.
- [ ] **Estabilidade do tempo:** o mesmo código tem o mesmo tempo de CPU com e sem carga?
  - `python3 loadtest/rodar.py pico --envios 200 --mix pesada:1 --runners 1 --boxes 1,2,4,8,16 --repeticoes 3 --rotulo estab`
  - Onde ver: tabela "Tempo de CPU medido pelo isolate" do `relatorio.md` (coluna CV). De 8 para 16 boxes, duas
    execuções passam a dividir o mesmo núcleo (SMT).
- [ ] **Resiliência:** derrubar um runner no meio da carga faz alguma solução se perder?
  - `python3 loadtest/rodar.py carga --taxa 40 --duracao 5m --runners 2 --boxes 2 --derrubar-runner 90 --rotulo queda`
  - Esperado: falhas + perdas = 0 e 100% de vereditos corretos. O `ocupacao.png` da execução marca a queda e a volta.
- [ ] **Uso de CPU por container:** quanto cada parte consome (runners, API, banco, broker e o próprio k6)?
  - É coletado em todo teste de envio. Para ver a curva conforme a carga sobe:
    `python3 loadtest/rodar.py rampa --taxa-final 150 --degraus 8 --rotulo cpu`
  - Onde ver: `recursos.csv` de cada execução, campo `recursos` do `resumo.json` e colunas `cpu_*` do `relatorio.csv`.
  - Falta: tabela e gráfico de CPU por container no `relatorio.py`.