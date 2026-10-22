# runner-cpp

Runner C++ do Juizin. Consome `submissions.cpp`, compila e executa cada solução no [isolate](https://github.com/ioi/isolate) contra os casos de teste oficiais e publica o resultado em `submissions.results`. Decisões: [ADR-002](../docs/adr/002-isolate-sandbox.md), [ADR-005](../docs/adr/005-runner-container-privilegiado.md), [ADR-006](../docs/adr/006-rabbitmq-mensageria.md) e [ADR-008](../docs/adr/008-runner-java-casos-endpoint-interno.md). O contrato das mensagens está em [docs/architecture.md, 3.3.1](../docs/architecture.md).

## Requisitos
- Docker em host com **cgroup v2**: `docker info --format '{{.CgroupVersion}}'` deve mostrar `2`. No Windows, veja [cgroup v2 no WSL2](#cgroup-v2-no-wsl2).
- Container **privilegiado** e com cgroup namespace próprio (`privileged: true` e `cgroup: private` no `compose.yaml`).
- RabbitMQ e a API no ar: o runner lê os casos de teste em `GET /api/internal/activities/{id}/test-cases`.

## Como subir
Na raiz do repositório, o ambiente completo (Postgres, RabbitMQ, API, frontend e runner):

```sh
docker compose up -d --build
docker compose logs -f runner-cpp
```

Com a API na IDE, suba só o broker e o runner, apontando o runner para o host:

```sh
docker compose up -d rabbitmq
JUIZIN_RUNNER_API_URL=http://host.docker.internal:8080 docker compose up -d --build runner-cpp
```

O build da imagem compila o jar (Maven, dentro do Docker) e o isolate (versão fixada em `ISOLATE_VERSION`).

## cgroup v2 no WSL2
Versões do WSL anteriores à 2.5.1 sobem o Linux em cgroup v1 (híbrido), e o `entrypoint.sh` para com `cgroup v2 não encontrado`. Para trocar para cgroup v2:

1. Atualize o WSL com `wsl --update` (a partir da 2.5.1, o padrão é cgroup v2). Se não puder atualizar, crie `%USERPROFILE%\.wslconfig` com:
   ```ini
   [wsl2]
   kernelCommandLine = cgroup_no_v1=all systemd.unified_cgroup_hierarchy=1
   ```
2. Rode `wsl --shutdown` (derruba todas as distros e os containers) e abra a distro de novo.
3. Confira: `stat -fc %T /sys/fs/cgroup` deve mostrar `cgroup2fs`, e `docker info --format '{{.CgroupVersion}}'`, `2`.

## Configuração (variáveis de ambiente)
| Variável                | Padrão                    | Uso                                                                                   |
|:------------------------|:--------------------------|:--------------------------------------------------------------------------------------|
| `RABBITMQ_HOST`         | `localhost`               | Host do RabbitMQ (`RABBITMQ_PORT`, `RABBITMQ_USER` e `RABBITMQ_PASSWORD` também).     |
| `JUIZIN_RUNNER_API_URL` | `http://localhost:8080`   | URL da API, para ler os casos de teste.                                               |
| `JUIZIN_RUNNER_TOKEN`   | token de desenvolvimento  | Token de serviço; precisa ser igual ao `juizin.runner.token` da API (mínimo 32 bytes). |
| `JUIZIN_RUNNER_BOXES`   | `2`                       | Execuções simultâneas (um box do isolate por execução, até 64).                       |
| `JAVA_OPTS`             | vazio                     | Opções da JVM.                                                                        |

## Como funciona
1. `PedidosListener` recebe o `PEDIDO_EXECUCAO` e publica `EXECUCAO_INICIADA`.
2. `CasosDeTesteClient` obtém os casos pelos ids, do cache ou da API.
3. `AvaliadorCpp` pega um box livre, compila com `g++ -std=c++17 -O2 -static` (dentro do isolate) e executa os casos em ordem até o primeiro que falhar. Os limites são os da atividade (tempo de CPU e memória do cgroup).
4. `ClassificadorExecucao` converte o meta-arquivo do isolate em veredito, e `ComparadorSaida` compara a saída ignorando espaços no fim das linhas e linhas em branco no final.
5. `PublicadorResultados` publica o `EXECUCAO_CONCLUIDA` e espera o confirm. Só então o pedido recebe ack.

Uma falha de infraestrutura (isolate, API ou broker) é tentada de novo e, depois, vai para a DLQ, onde a API marca a solução como `FALHA`.

Os testes unitários (`ComparadorSaida`, `MetaIsolate`, `ClassificadorExecucao`) não precisam do isolate.
