# Architecture Decision Records (ADR)

Cada ADR registra **uma** decisão técnica relevante, com o contexto, a decisão tomada, as consequências (prós e contras aceitos) e as alternativas consideradas.

- Um arquivo por decisão, numerado em sequência (`NNN-titulo.md`).
- ADRs são **imutáveis**: se uma decisão mudar, crie um novo ADR e marque o antigo como `Substituída por ADR-NNN`.
- Status possíveis: `Proposta`, `Aceita`, `Substituída`, `Descartada`.

| ADR                                            | Decisão                                                  | Status   |
|:-----------------------------------------------|:---------------------------------------------------------|:---------|
| [001](001-kafka-mensageria.md)                       | Kafka como mensageria entre API e runners                | Substituída por 006 |
| [002](002-isolate-sandbox.md)                        | isolate como sandbox de cada execução                    | Aceita              |
| [003](003-sse-notificacao-resultado.md)              | SSE para notificar o resultado ao cliente                | Aceita              |
| [004](004-transactional-outbox.md)                   | Transactional Outbox para publicar soluções no Kafka     | Substituída por 007 |
| [005](005-runner-container-privilegiado.md)          | Runner em container privilegiado com cgroups v2          | Aceita              |
| [006](006-rabbitmq-mensageria.md)                    | RabbitMQ como mensageria entre API e runners             | Aceita              |
| [007](007-publicacao-apos-commit-reconciliacao.md)   | Publicação após o commit com job de reconciliação        | Aceita              |
| [008](008-runner-java-casos-endpoint-interno.md)     | Runner em Java/Spring Boot; casos de teste por endpoint interno | Aceita       |
