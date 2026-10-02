# ADR-003: Notificar o resultado ao cliente via SSE

## Status
Proposta (2026-10-02)

## Contexto
O envio de solução é assíncrono: a API responde `202 Accepted {solucaoId}` e o veredito chega depois, quando a API consome `submissions.results`. O frontend precisa mostrar a mudança de status (`PENDENTE → EM_EXECUCAO → CONCLUIDA`) e o veredito sem que o usuário recarregue a página.

A comunicação necessária é **unidirecional** (servidor → cliente) e de baixa frequência (poucos eventos por solução).

## Decisão
Usar **Server-Sent Events (SSE)**:
- O frontend abre `GET /api/submissions/{id}/events` (ou um stream por usuário) após o envio.
- A API emite um evento a cada mudança de status e um evento final com o veredito, e então fecha o stream.
- `GET /api/submissions/{id}` continua disponível como **fallback** (reconexão, recarregamento da página, histórico).

## Consequências
- ✅ Simples: HTTP comum, suportado nativamente pelo navegador (`EventSource`) e pelo Spring (`SseEmitter` / `Flux<ServerSentEvent>`).
- ✅ Reconexão automática do `EventSource`.
- ✅ Menos requisições que polling.
- ⚠️ Com **várias réplicas da API**, o resultado pode ser consumido por uma réplica diferente daquela que mantém a conexão SSE do cliente. Será preciso distribuir o evento entre réplicas (ex.: cada réplica consome `submissions.results` com um group próprio, ou Redis pub/sub).
- ⚠️ Conexões abertas consomem recursos: definir timeout e fechar o stream após o veredito.
- ⚠️ Proxies e load balancers precisam permitir conexões longas e sem buffering.

## Alternativas consideradas
- **Polling** (`GET` periódico): o mais simples, porém gera muitas requisições e tem latência proporcional ao intervalo.
- **WebSocket**: bidirecional, o que é desnecessário aqui, e mais complexo (protocolo próprio, STOMP, sessões).
