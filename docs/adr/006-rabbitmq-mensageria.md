# ADR-006: Usar RabbitMQ como mensageria entre API e runners

## Status
Aceita (2026-10-13). Substitui o [ADR-001](001-kafka-mensageria.md).

## Contexto
As necessidades do [ADR-001](001-kafka-mensageria.md) continuam valendo: a execução de uma solução é lenta (compilação + N casos de teste), a carga tem picos perto do fim dos prazos dos grupos e cada linguagem precisa de um runtime próprio, com escala independente.

Uma reavaliação mostrou que o Kafka é desproporcional para o MVP:
- **Custo de máquina:** o broker roda em JVM, com heap padrão de 1 GB, o que pesa para quem roda a stack inteira em uma máquina de desenvolvimento.
- **Modelo inadequado:** o Juizin precisa de uma **fila de jobs (work queue)**, em que cada solução é executada por **um** runner e confirmada ao final. Ele não precisa de um **stream de eventos** com vários consumidores, retenção e replay. Partições, offsets, consumer groups e rebalance trazem complexidade sem retorno nesse cenário.
- **Fonte da verdade:** o PostgreSQL já guarda todas as soluções e vereditos. Por isso, o replay do broker não é necessário.

## Decisão
Usar **RabbitMQ 4.x** (imagem `rabbitmq:4-management`), com um nó no MVP:
- **Exchange direct** `juizin.submissions`.
- **Uma fila por linguagem**: `submissions.<lang>` (inicialmente só `submissions.cpp`), ligada ao exchange com binding key igual ao nome da fila. O mapeamento linguagem → fila fica em `Linguagem.fila`, então uma linguagem nova não exige alteração no fluxo da API.
- **Uma fila única de resultados**: `submissions.results`, consumida pela API.
- **Durabilidade:** filas **quorum** duráveis, mensagens persistentes e **publisher confirms** na API.
- **Runners como competing consumers:** todas as réplicas de uma linguagem consomem a mesma fila, com **ack manual** depois de publicar o resultado e `prefetch` igual ao número de boxes simultâneos da réplica.
- **Falhas:** a mensagem rejeitada, ou que passa do `x-delivery-limit`, vai para o DLX `juizin.dlx` e dele para a fila `submissions.dlq`. A API consome a DLQ e marca a solução como `FALHA` (`ERRO_INTERNO`).
- **Desacoplamento:** a API publica e consome por meio de portas próprias (evento `SolucaoEnviadaEvent`, publicador e listener de resultados). Trocar de broker afeta só esses adaptadores e o worker do runner.

## Consequências
- ✅ Leve: um container de ~100–200 MB de memória em repouso.
- ✅ Semântica de fila nativa: ack por mensagem, redelivery, DLQ e limite de entregas sem código na aplicação.
- ✅ O paralelismo não é limitado por partições: para escalar, basta adicionar consumidores.
- ✅ A UI de administração (porta 15672) ajuda a inspecionar filas e mensagens no desenvolvimento.
- ✅ Spring AMQP maduro, e o Spring Boot cria a conexão automaticamente a partir do `compose.yaml`.
- ⚠️ Sem retenção nem replay: depois do ack, a mensagem deixa de existir. Isso é aceito porque o PostgreSQL é a fonte da verdade e o [ADR-007](007-publicacao-apos-commit-reconciliacao.md) cobre mensagens perdidas.
- ⚠️ Entrega **at-least-once** (redelivery quando não há ack): o processamento deve ser idempotente pelo `solucaoId`.
- ⚠️ O `consumer_timeout` (padrão de 30 min) precisa ser maior que a execução mais longa, senão o canal é fechado e a mensagem é reentregue.
- ⚠️ Throughput menor que o do Kafka, o que é irrelevante na escala prevista (ver abaixo).

## Caminho de escala
O cenário de produção previsto (escala federal, muitos usuários enviando soluções para diferentes runners ao longo do tempo) continua sendo uma **work queue de alto volume**: cada solução é consumida uma única vez. Os estágios de evolução são:

| Estágio                          | Mecanismo                                                                                                                                                                                                       |
|:---------------------------------|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| MVP / uma instituição            | Um nó com uma fila por linguagem. Para escalar, basta adicionar runners consumindo a mesma fila.                                                                                                                 |
| Regional                         | Cluster de 3 nós com filas quorum replicadas via Raft (alta disponibilidade). Uma fila muito carregada é dividida com o **consistent-hash exchange**, que reparte por hash do `solucaoId` em `submissions.cpp.0..N`, com os líderes espalhados entre os nós. |
| Nacional / vários datacenters    | Brokers regionais ligados por **Federation/Shovel**, com roteamento por região na routing key. Se surgir a necessidade de log particionado com replay, usar **Super Streams** (streams particionados nativos do RabbitMQ). |

**O gargalo real é a CPU dos runners, não o broker.** Em ordem de grandeza, 1.000 soluções/s com ~2s de CPU cada exigem cerca de 2.000 núcleos de runner, enquanto uma única fila RabbitMQ suporta dezenas de milhares de mensagens/s (o valor depende do hardware).

### Gatilhos para reavaliar o Kafka
- Surgirem vários consumidores independentes dos mesmos eventos (ranking, antiplágio, analytics, auditoria).
- For necessário replay ou retenção longa de eventos.
- O throughput sustentado ultrapassar o que cluster + sharding resolvem.

Mesmo nesses casos, uma opção é **adicionar** o Kafka para analytics (capturando as mudanças do PostgreSQL via CDC) e manter o RabbitMQ para os jobs.

## Alternativas consideradas
- **Kafka** ([ADR-001](001-kafka-mensageria.md)): desproporcional no MVP. O paralelismo é limitado pelo número de partições, e o problema é de fila de jobs, não de stream.
- **PostgreSQL como fila** (`SELECT ... FOR UPDATE SKIP LOCKED`): não exige infraestrutura extra, mas obrigaria o runner a ter credenciais do banco, o que contradiz o [ADR-005](005-runner-container-privilegiado.md).
- **Redis Streams**: leve, porém com menos garantias de durabilidade.
