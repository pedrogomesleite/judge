# ADR-004: Usar Transactional Outbox para publicar soluções no Kafka

## Status
Substituída por [ADR-007](007-publicacao-apos-commit-reconciliacao.md) (2026-10-13). Proposta em 2026-10-02.

## Contexto
Ao receber uma solução, a API precisa fazer **duas escritas em sistemas diferentes**:
1. salvar a `Solucao` no PostgreSQL (`PENDENTE`);
2. publicar a mensagem em `submissions.<lang>` no Kafka.

Não existe transação única entre PostgreSQL e Kafka (**dual write**). Isso gera dois cenários de falha:
- A solução é salva, mas a publicação falha: ela fica **presa em `PENDENTE`** para sempre.
- A publicação acontece, mas o commit no banco falha: o runner executa uma solução **que não existe** no banco.

## Decisão
Usar o padrão **Transactional Outbox**:
- Na **mesma transação** que salva a `Solucao`, a API insere um registro na tabela `outbox` (tópico, chave = `solucaoId`, payload, criadoEm, publicadoEm).
- Um **publicador** (job agendado no Spring, ex.: `@Scheduled` com `SELECT ... FOR UPDATE SKIP LOCKED`) lê os registros não publicados, envia ao Kafka e marca `publicadoEm`.
- Os consumidores tratam mensagens duplicadas de forma idempotente pelo `solucaoId` (ver [ADR-001](001-kafka-mensageria.md)).

## Consequências
- ✅ Garantia de que toda solução persistida será publicada em algum momento (consistência eventual).
- ✅ Não exige transação distribuída.
- ✅ A tabela `outbox` serve de log de auditoria do que foi enviado.
- ⚠️ Adiciona latência igual ao intervalo do publicador (ex.: 100–500ms).
- ⚠️ Entrega at-least-once: uma falha entre o envio e a marcação causa reenvio, o que já é coberto pela idempotência.
- ⚠️ É preciso limpar periodicamente os registros antigos já publicados.

## Alternativas consideradas
- **Publicar direto após o commit** (`@TransactionalEventListener(AFTER_COMMIT)`): simples, mas a mensagem se perde se a aplicação cair entre o commit e o envio.
- **Job de reconciliação** que republica soluções `PENDENTE` antigas: válido como **rede de segurança complementar**, mas sozinho tem latência alta.
- **CDC com Debezium** lendo a tabela `outbox`: mais robusto e sem polling, porém adiciona Kafka Connect e Debezium à infraestrutura. Pode ser adotado no futuro sem alterar o modelo da tabela.
