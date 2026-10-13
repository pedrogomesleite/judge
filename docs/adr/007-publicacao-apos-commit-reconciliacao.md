# ADR-007: Publicar a solução após o commit, com job de reconciliação

## Status
Aceita (2026-10-13). Substitui o [ADR-004](004-transactional-outbox.md).

## Contexto
Ao receber uma solução, a API faz **duas escritas em sistemas diferentes**: salva a `Solucao` no PostgreSQL (`PENDENTE`) e publica a mensagem na fila da linguagem no RabbitMQ ([ADR-006](006-rabbitmq-mensageria.md)). Como não existe transação única entre os dois (**dual write**), a solução pode ficar presa em `PENDENTE` se a publicação falhar.

O [ADR-004](004-transactional-outbox.md) propunha o Transactional Outbox, que exige uma tabela extra, um publicador agendado e uma limpeza periódica. Isso é mais do que o MVP precisa.

## Decisão
**Caminho normal (imediato):**
- `SolucaoService.enviar` emite o evento de domínio `SolucaoEnviadaEvent`.
- Um `@TransactionalEventListener(phase = AFTER_COMMIT)` publica a mensagem em `linguagem.fila` com publisher confirm. Quando o confirm chega, grava `Solucao.publicadaEm`.
- A latência é de milissegundos: **o usuário não espera nenhum job**.

**Rede de segurança (job de reconciliação):**
- Um job `@Scheduled` republica as soluções `PENDENTE` com `publicadaEm IS NULL` e `enviadoEm` mais antigo que a idade mínima.
- Configuração: `juizin.reconciliacao.intervalo=PT30S` e `juizin.reconciliacao.idade-minima=PT30S`.
- O job também roda logo após o startup, para cobrir uma queda da API entre o commit e o envio.

**Por que filtrar por `publicadaEm`, e não só por `PENDENTE`:** uma solução que está esperando numa fila cheia continua `PENDENTE`. Se o job republicasse com base só no status, duplicaria mensagens justamente nos picos, quando os runners estão mais ocupados.

### Espera do usuário por cenário

| Cenário                                       | Espera extra                                            |
|:----------------------------------------------|:--------------------------------------------------------|
| Normal                                        | Nenhuma: a publicação acontece logo após o commit       |
| RabbitMQ indisponível                         | Até ~60s (idade mínima + intervalo) depois que ele volta |
| API caiu entre o commit e a publicação        | Até a primeira execução do job após o restart           |

Os 30s equilibram os dois lados: um intervalo menor aumenta as consultas ao banco sem ganho, porque falhas são raras; um intervalo maior aumenta a espera quando a falha acontece.

## Consequências
- ✅ Sem tabela extra nem limpeza periódica.
- ✅ Sem espera no caminho normal.
- ✅ Cobre tanto a indisponibilidade do broker quanto a queda da API.
- ⚠️ Pode haver duplicata se a API cair entre o confirm e a gravação de `publicadaEm`. Isso já é coberto pela idempotência pelo `solucaoId`.
- ⚠️ Com várias réplicas da API, o job precisa de `SELECT ... FOR UPDATE SKIP LOCKED` ou de rodar em uma única instância.

## Alternativas consideradas
- **Transactional Outbox** ([ADR-004](004-transactional-outbox.md)): mais robusto, mas com mais código e infraestrutura. Pode ser retomado se a escala exigir.
- **Publicar após o commit sem reconciliação**: simples, mas a mensagem se perde se a aplicação cair entre o commit e o envio.
