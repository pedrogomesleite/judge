# ADR-003: Notificar o resultado ao cliente via SSE

## Status
Aceita (2026-10-21). Proposta em 2026-10-02.

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
- ⚠️ Com **várias réplicas da API**, o resultado pode ser consumido por uma réplica diferente daquela que mantém a conexão SSE do cliente. Será preciso distribuir o evento entre réplicas (ex.: um exchange fanout de notificações no RabbitMQ, com uma fila exclusiva por réplica, ou Redis pub/sub).
- ⚠️ Conexões abertas consomem recursos: definir timeout e fechar o stream após o veredito.
- ⚠️ Proxies e load balancers precisam permitir conexões longas e sem buffering.

## Implementação (2026-10-21): stream por grupo
A primeira implementação é um stream **por grupo**, usado na tela do grupo:
- `GET /api/groups/{id}/events` (`SseEmitter`, `EventosGrupoService`), só para membros do grupo. O cliente envia `Accept: text/event-stream, application/json` para receber os erros (403/404) como `ErroResponse`.
- Os eventos são de **invalidação**: o nome do evento é o tipo da alteração (`GRUPO`, `ATIVIDADES`, `MEMBROS`, `SOLUCOES`) e não há dados sensíveis. O cliente busca de novo pelos endpoints, que continuam aplicando as permissões de cada papel. O primeiro evento é `CONECTADO`.
- Os services publicam `GrupoAlteradoEvent` e o envio acontece **depois do commit** (`@TransactionalEventListener(AFTER_COMMIT)`). Um comentário de heartbeat a cada 25 s mantém a conexão aberta em proxies; o stream expira em 30 min e o cliente reconecta, com espera crescente, recarregando tudo.
- O frontend usa `fetch` em streaming em vez de `EventSource`, porque o `EventSource` não envia o header `Authorization` (JWT).
- O dispatch `ASYNC` do servlet é liberado no Spring Security, porque a requisição original já foi autorizada.
- As inscrições ficam em memória: vale a ressalva acima sobre várias réplicas.

O stream por solução (`/api/submissions/{id}/events`) continua como evolução; enquanto isso, a tela da atividade consulta o status por polling.

## Implementação (2026-10-22): stream por solução
Com o runner real ([ADR-006](006-rabbitmq-mensageria.md), [ADR-008](008-runner-java-casos-endpoint-interno.md)), a tela da atividade troca o polling pelo stream da solução:
- `GET /api/submissions/{id}/events` (`SseEmitter`, `EventosSolucaoService`), com as mesmas permissões do `GET /api/submissions/{id}`: o autor e os avaliadores do grupo da solução. Os erros (403/404) chegam como `ErroResponse`, como no stream do grupo.
- Diferente do stream do grupo, os eventos **levam dados**: o nome do evento é o status (`PENDENTE`, `EM_EXECUCAO`, `CONCLUIDA`, `FALHA`) e o dado é o `SolucaoResponse`. Os inscritos já passaram pela verificação de permissão, e o conteúdo é o mesmo do GET.
- O primeiro evento é o estado atual, e o stream é encerrado depois do veredito. O cliente reconecta com espera crescente enquanto a solução não termina; como cada conexão começa pelo estado atual, nada se perde. O cliente nunca deixa o status regredir, o que cobre a corrida entre a leitura inicial e um evento da mesma mudança.
- O `SolucaoService` publica `SolucaoAlteradaEvent` a cada mudança de status, e o envio acontece depois do commit. O heartbeat (25 s) e o mapa de inscrições em memória são compartilhados com o stream do grupo (`InscricoesSse`), com a mesma ressalva sobre várias réplicas.
- A lista "minhas respostas" (`SolucoesView`) continua com polling enquanto houver solução em avaliação.

## Alternativas consideradas
- **Polling** (`GET` periódico): o mais simples, porém gera muitas requisições e tem latência proporcional ao intervalo.
- **WebSocket**: bidirecional, o que é desnecessário aqui, e mais complexo (protocolo próprio, STOMP, sessões).
