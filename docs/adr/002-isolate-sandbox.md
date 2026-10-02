# ADR-002: Usar isolate como sandbox de cada execução

## Status
Aceita (2026-10-02)

## Contexto
O Juizin executa código **não confiável** enviado por usuários. O runner de cada linguagem é um container de longa duração que processa muitas soluções. Se o código rodasse direto nesse container, uma solução maliciosa ou com bug poderia:
- ler ou alterar arquivos de outras soluções;
- acessar a rede;
- esgotar CPU, memória ou processos (fork bomb);
- derrubar ou comprometer o próprio runner.

Também é preciso **medir com precisão** o tempo e a memória de cada execução para emitir vereditos como `TEMPO_EXCEDIDO` e `MEMORIA_EXCEDIDA`.

## Decisão
Toda **compilação e execução** de uma solução roda dentro do **[isolate](https://github.com/ioi/isolate)** (sandbox usado na IOI, CMS e Judge0), invocado pelo runner:
- Um **box** (diretório de trabalho descartável) por execução, identificado por `--box-id`. Cada réplica do runner executa N boxes em paralelo.
- **Namespaces Linux**: sem rede, com árvore de PIDs e filesystem próprios.
- **cgroups v2**: limite de memória, de CPU e de número de processos.
- Limites de tempo de **CPU** e de **wall time**, além de tamanho máximo de arquivo.
- O resultado (tempo, memória, exit code, sinal) é lido do **meta-arquivo** gerado pelo isolate.
- O runner, **fora do sandbox**, compara a saída e decide o veredito.

## Consequências
- ✅ Isolamento por execução, mesmo em um runner de longa duração. Isso resolve o dilema entre container fixo e container dinâmico.
- ✅ Medição de tempo e memória precisa e padronizada.
- ✅ Leve: sem cold start de container por execução.
- ⚠️ Exige container **privilegiado / com cgroups v2** (ver [ADR-005](005-runner-container-privilegiado.md)).
- ⚠️ Funciona apenas em Linux. Em desenvolvimento no Windows, rodar via Docker/WSL2.
- ⚠️ A compilação também precisa rodar no sandbox, porque o g++ pode ser abusado (ex.: `#include "/dev/random"`, binários enormes).
- ⚠️ Para evitar o efeito de vizinho barulhento, é preciso fixar CPU por box e medir tempo de CPU.

## Alternativas consideradas
- **nsjail**: equivalente em isolamento, mas menos usado em juízes online e sem o meta-arquivo pronto para vereditos.
- **gVisor / Firecracker**: isolamento mais forte (kernel em espaço de usuário ou microVM), porém mais pesado e complexo de operar.
- **Apenas limites do Docker** (`--memory`, `--network none`): granularidade de container e não de execução, além de não isolar soluções entre si dentro do mesmo runner.
