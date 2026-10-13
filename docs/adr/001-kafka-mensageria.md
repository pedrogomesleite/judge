# ADR-001: Usar Kafka como mensageria entre API e runners

## Status
Substituída por [ADR-006](006-rabbitmq-mensageria.md) (2026-10-13). Aceita originalmente em 2026-10-02.

## Contexto
Executar o código de uma solução leva de alguns segundos a dezenas de segundos (compilação + N casos de teste). A API não pode ficar bloqueada esperando essa execução. Além disso:
- Cada linguagem precisa de um runtime próprio (imagem com compilador/interpretador + isolate).
- A carga é irregular, com picos perto do fim de prazos de grupos.
- Cada linguagem deve escalar de forma independente.

## Decisão
Usar **Kafka** como mensageria assíncrona entre a API e os runners:
- **Um tópico por linguagem**: `submissions.<lang>` (inicialmente só `submissions.cpp`), com chave de partição `solucaoId`.
- **Um consumer group por linguagem**: `runner-<lang>`. As réplicas do runner dividem as partições.
- **Um tópico único de resultados**: `submissions.results`, consumido pela API.
- O mapeamento linguagem → tópico fica na entidade `Linguagem`, então uma linguagem nova não exige alteração no fluxo da API.

## Consequências
- ✅ A API responde `202 Accepted` imediatamente; execução desacoplada.
- ✅ Buffer natural para picos; mensagens retidas se um runner cair.
- ✅ Escala horizontal por linguagem adicionando réplicas ao consumer group.
- ✅ Retenção permite reprocessar mensagens (replay).
- ⚠️ O paralelismo máximo por linguagem é limitado ao **número de partições**, que deve ser definido com folga (ex.: 6–12).
- ⚠️ Não há retry/DLQ por mensagem nativo: precisa ser implementado na aplicação (ex.: tópico `submissions.<lang>.dlq`).
- ⚠️ Entrega **at-least-once**: o processamento deve ser idempotente pelo `solucaoId`.
- ⚠️ Execuções longas podem estourar o `max.poll.interval.ms` e provocar rebalance. É preciso ajustar o timeout e o `max.poll.records`.
- ⚠️ Mensagens limitadas a ~1MB por padrão: a mensagem leva o código e os IDs dos casos de teste, e não o conteúdo dos casos.
- ⚠️ Custo operacional de manter um cluster Kafka.

## Alternativas consideradas
- **RabbitMQ**: semântica de work queue nativa (ack por mensagem, retry, DLQ), mas sem retenção e replay.
- **Redis Streams**: mais leve de operar, porém com menos garantias de durabilidade.
- **Kafka share groups (KIP-932)**: semântica de fila sobre Kafka. Pode ser reavaliado quando estiver estável na versão adotada.
- **Container criado dinamicamente por solução**: descartado pelo cold start (1–3s por execução) e pelo custo de orquestração.
