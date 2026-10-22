# ADR-008: Worker do runner em Java/Spring Boot, com os casos de teste lidos de um endpoint interno

## Status
Aceita (2026-10-22). Resolve a decisão em aberto da seção 3.7 da arquitetura (linguagem do worker do runner) e detalha como o runner lê os casos de teste, que o [ADR-005](005-runner-container-privilegiado.md) já permitia.

## Contexto
O runner C++ consome `submissions.cpp` ([ADR-006](006-rabbitmq-mensageria.md)), compila e executa cada solução no isolate ([ADR-002](002-isolate-sandbox.md)) e publica o resultado em `submissions.results`. Faltava decidir:
- **A stack do worker.** A arquitetura listava Java/Spring AMQP ou um processo leve.
- **Como o runner obtém a entrada e a saída esperada dos casos de teste.** O runner não tem credenciais do banco nem da API (ADR-005), e os casos podem ser grandes demais para viajar em toda mensagem.

## Decisão
**Worker em Java 21 + Spring Boot** (`runner-cpp/`, projeto Maven próprio):
- Mesma stack e mesmas bibliotecas da API: Spring AMQP (listener, publisher confirms, retry), o mesmo conversor JSON com os ids de tipo do contrato (`__TypeId__`) e `RestClient`.
- Um consumidor por box do isolate, com `prefetch=1`: o prefetch total da réplica é igual ao número de boxes (`juizin.runner.boxes`).
- O ack (modo AUTO do Spring) só sai depois que o broker confirma o resultado. Se o runner cair no meio da execução, a mensagem é reentregue; depois de `x-delivery-limit` entregas, ela vai para a DLQ.
- Os casos rodam em ordem e **param no primeiro que falha**, para poupar a CPU do runner, que é o gargalo (ADR-006).
- Container privilegiado com cgroup namespace próprio. O `entrypoint.sh` delega um cgroup v2 ao isolate (controlador `memory`) e inicia o worker como usuário sem privilégios; o isolate é setuid root (ADR-005).

**Casos de teste por id, lidos de um endpoint interno:**
- O `PEDIDO_EXECUCAO` leva só os ids dos casos `OFICIAL`. O runner os busca em `GET /api/internal/activities/{id}/test-cases`.
- O endpoint fica numa `SecurityFilterChain` própria (`/api/internal/**`), autenticada por um **token de serviço** (`juizin.runner.token`), e não pelo JWT dos usuários. O token só dá acesso aos endpoints internos, hoje apenas à leitura dos casos de teste.
- Os casos de teste são imutáveis (só se cria um caso ou se altera o campo `publico`), então o runner os guarda em cache pelo id, sem expiração, com limite de tamanho (os menos usados saem primeiro). A API só é chamada quando falta algum caso do pedido.

## Consequências
- ✅ O time mantém uma stack só e reaproveita no runner o padrão de código da API.
- ✅ As mensagens ficam pequenas, e o cache evita reler os mesmos casos a cada solução.
- ✅ O runner continua sem credenciais do banco e com um token de escopo mínimo.
- ⚠️ A imagem do runner é maior que a de um processo leve, porque inclui um JRE (~200 MB).
- ⚠️ O contrato (`Mensageria`, mensagens, `Veredito`) é **espelhado** nos dois projetos e precisa ser mantido em sincronia.
- ⚠️ O runner depende da API para ler os casos que ainda não estão no cache. Com a API fora do ar, a mensagem é tentada de novo e, se a falha continuar, vai para a DLQ, onde a solução vira `FALHA`.
- ⚠️ Se um dia os casos de teste puderem ser editados, o cache por id deixa de servir: será preciso versionar os casos ou criar um novo id a cada edição.
- ⚠️ Com o fail-fast, `ResultadoCasoDeTeste` só registra os casos executados.
- ⚠️ O erro de compilação não é devolvido ao usuário, porque o contrato ainda não tem campo para a mensagem do compilador.

## Alternativas consideradas
- **Casos inline na mensagem** (entrada + saída esperada): dispensa o endpoint e o token, mas repete casos possivelmente grandes em toda mensagem.
- **Worker em Go ou Python:** imagem menor, mas com uma stack nova no projeto e sem o reaproveitamento do Spring AMQP.
