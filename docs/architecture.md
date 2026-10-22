# 1. Visão geral

O **Juizin** é uma plataforma de correção de código fonte automatizada 

# 2. Personas e Atores (Quem usa)

Existe um único tipo de conta: o **Usuário** (login e senha). **Estudante** e **Avaliador** não são perfis globais, e sim **papéis dentro de cada Grupo de Atividade** (`MembroGrupo.papel`). O mesmo **Usuário** pode ser **Avaliador** em um grupo e **Estudante** em outro.

| Persona                       | Descrição                                                                                  | Ações                                                                                                                                                                                    |
|:------------------------------|:-------------------------------------------------------------------------------------------|:-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Visitante** (sem login)     | Quem acessa o Juizin sem entrar                                                            | lista as atividades e vê o enunciado, os casos de teste públicos (exemplos) e as linguagens; para enviar uma solução, precisa entrar ou criar uma conta                                 |
| **Usuário** (fora de grupo)   | Qualquer usuário autenticado                                                               | cria atividades e os casos de teste oficiais das próprias atividades (podendo marcar alguns como públicos), envia soluções avulsas para qualquer atividade, cria grupos de atividades, visualiza as próprias soluções |
| **Estudante** (no grupo)      | Membro de um grupo, que entrou pelo link de convite                                        | envia soluções para as atividades do grupo, cria e vê casos de teste comunitários (visíveis só para membros do grupo), compartilha o link de convite, visualiza as próprias soluções, acompanha o placar do grupo e, ao final, a própria pontuação por atividade |
| **Avaliador** (no grupo)      | Criador do grupo ou membro promovido por outro **Avaliador**                               | tudo que o **Estudante** faz, mais: gerencia atividades, pesos e datas do grupo, remove estudantes antes do início, inicia/finaliza o grupo, promove membros a **Avaliador**, visualiza e compara as soluções de todos os membros do grupo, exporta o resultado final em CSV |

# 3. Visão Arquitetural de Alto Nível

## 3.1 Visão geral

A arquitetura do **Juizin** é **assíncrona e orientada a mensagens**. O **Usuário** envia uma solução para a API, que valida as regras de domínio (ver 5.4), persiste a **Solução** e a publica na fila da linguagem correspondente (RabbitMQ). Um **Runner** daquela linguagem consome a mensagem, compila e executa o código em um sandbox (**isolate**), compara a saída obtida com a saída esperada de cada caso de teste oficial e publica o veredito em uma fila de resultados. A API consome esse resultado, atualiza a **Solução** e notifica o cliente.

Neste primeiro momento **apenas o Runner C/C++ é implementado**. A arquitetura já prevê novas linguagens e mais réplicas (ver 3.5).

## 3.2 Diagrama de Containers (C4 nível 2)

```mermaid
flowchart LR
    U[Estudante / Avaliador<br/>Frontend Vue 3] -->|HTTPS /api| API[Juizin API<br/>Spring Boot]
    API --> DB[(PostgreSQL)]
    API -->|submissions.cpp| K[[RabbitMQ]]
    K -->|competing consumers - prefetch| R[Runner C/C++<br/>N réplicas]
    R -->|executa cada caso em| I[isolate<br/>sandbox por execução]
    R -->|submissions.results| K
    K --> API
    API -. SSE .-> U
    K -. "submissions.&lt;lang&gt; (futuro)" .-> RF[Runner &lt;lang&gt;<br/>futuro]
```

## 3.3 Diagrama de Sequência: fluxo de envio de solução

```mermaid
sequenceDiagram
    actor E as Usuário
    participant API as Juizin API (Spring)
    participant DB as PostgreSQL
    participant K as RabbitMQ
    participant R as Runner C/C++
    participant I as isolate

    E->>API: POST /api/submissions (código, linguagem, atividade, grupo opcional)
    API->>API: valida regras de domínio (membro, grupo EM_ANDAMENTO, atividade no grupo)
    API->>DB: salva Solucao (PENDENTE)
    API-->>E: 202 Accepted {solucaoId}
    API->>K: após o commit, publica em submissions.cpp
    API->>DB: grava publicadaEm (ADR-007)
    K->>R: entrega a solução (prefetch)
    R->>I: compila (g++) dentro do sandbox
    alt erro de compilação
        R->>K: resultado ERRO_COMPILACAO
    else compilou
        loop cada caso de teste OFICIAL
            R->>I: executa com stdin do caso + limites
            I-->>R: stdout + meta (tempo, memória, status)
            R->>R: compara saída obtida x esperada
        end
        R->>K: publica em submissions.results (veredito)
    end
    R->>K: ack da mensagem de submissions.cpp
    K->>API: entrega o resultado
    API->>DB: atualiza Solucao (status, veredito, tempo, memória)
    API-->>E: notifica (SSE) / GET /api/submissions/{id}
```

O runner publica `EXECUCAO_INICIADA` em `submissions.results` assim que consome a mensagem (a solução passa para `EM_EXECUCAO`) e busca os casos de teste no endpoint interno (ver 3.3.1), usando o cache quando pode. O ack só sai depois que o broker confirma o resultado.

> Os nomes técnicos de filas e endpoints (`submissions.*`, `/api/submissions`) são contratos técnicos e permanecem em inglês.

## 3.3.1 Contrato das mensagens

As constantes ficam na classe `Mensageria`, espelhada na API (`com.packges.judge.messaging`) e no runner (`com.packges.runner.mensageria`). As duas pontas declaram as filas com os **mesmos argumentos**, então a ordem de subida não importa. Argumentos diferentes fazem o RabbitMQ recusar a declaração (`PRECONDITION_FAILED`).

| Elemento                         | Definição                                                                                                                                                                      |
|:---------------------------------|:-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `juizin.submissions`             | Exchange direct durável. Cada fila é ligada com binding key igual ao próprio nome.                                                                                             |
| `submissions.<lang>`             | Fila quorum da linguagem (`Linguagem.fila`), com `x-dead-letter-exchange=juizin.dlx` e `x-delivery-limit=3`. A API a declara sob demanda ao publicar, e o runner, ao subir.     |
| `submissions.results`            | Fila quorum única de resultados, com os mesmos argumentos, consumida pela API.                                                                                                 |
| `juizin.dlx` → `submissions.dlq` | Exchange fanout e fila quorum (sem DLX), consumida pela API.                                                                                                                   |

O corpo é JSON, e o tipo vai no header `__TypeId__`:

| `__TypeId__`         | Fila                  | Corpo                                                                                                            | Efeito na API                               |
|:---------------------|:----------------------|:-----------------------------------------------------------------------------------------------------------------|:--------------------------------------------|
| `PEDIDO_EXECUCAO`    | `submissions.<lang>`  | `solucaoId`, `atividadeId`, `linguagem`, `codigo`, `limiteTempoMs`, `limiteMemoriaMb`, `casosDeTesteIds`        | publicado pela API                          |
| `EXECUCAO_INICIADA`  | `submissions.results` | `solucaoId`                                                                                                      | `PENDENTE → EM_EXECUCAO`                    |
| `EXECUCAO_CONCLUIDA` | `submissions.results` | `solucaoId`, `veredito`, `tempoMs`, `memoriaKb`, `casos[]` (`casoDeTesteId`, `veredito`, `tempoMs`, `memoriaKb`) | `CONCLUIDA` (ou `FALHA`, se `ERRO_INTERNO`) |

- **Publicação:** mensagens persistentes, publisher confirms e `mandatory` (uma mensagem sem fila de destino conta como falha). A API só grava `publicadaEm` depois do confirm ([ADR-007](adr/007-publicacao-apos-commit-reconciliacao.md)).
- **Falhas:** a API e o runner tentam de novo, com espera crescente, e depois rejeitam a mensagem sem requeue, o que a manda para a DLQ. Na DLQ, um `PEDIDO_EXECUCAO` ou um `EXECUCAO_CONCLUIDA` marca a solução como `FALHA` (`ERRO_INTERNO`); um `EXECUCAO_INICIADA` é descartado.
- **Casos de teste:** a mensagem leva só os ids. O runner lê os casos em `GET /api/internal/activities/{id}/test-cases`, com o token de serviço (`Authorization: Bearer <juizin.runner.token>`), e os guarda em cache pelo id, porque eles são imutáveis ([ADR-008](adr/008-runner-java-casos-endpoint-interno.md)).
- **Execução:** os casos rodam em ordem, até o primeiro que falha. `casos[]` traz só os casos executados, e `tempoMs`/`memoriaKb` são os maiores entre eles.

## 3.4 Responsabilidades por componente e tecnologia

| Componente    | Tecnologia                                                     | Responsável por                                                                                                                                                                                                                                                                                  | NÃO é responsável por                         |
|:--------------|:---------------------------------------------------------------|:-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:----------------------------------------------|
| Frontend      | Vue 3 + Pinia + PrimeVue 4 (`juizin/`)                         | Catálogo público de atividades; editor e envio da solução; exibir status e veredito, atualizados pelo SSE da solução (`/api/submissions/{id}/events`); tela do grupo por papel e status, atualizada pelo SSE do grupo (`/api/groups/{id}/events`); a lista de respostas do usuário ainda usa polling | Validar o resultado                           |
| Juizin API    | Spring Boot (Java 21)                                          | Autenticação; autorização por papel no grupo; validar a solução (linguagem suportada, tamanho do código, regras de grupo); persistir; publicar na fila da linguagem; consumir resultados; atualizar o status; notificar o cliente                                                                | Executar código do usuário                    |
| Banco         | PostgreSQL                                                     | Fonte da verdade: usuários, atividades, casos de teste, grupos e membros, soluções e vereditos                                                                                                                                                                                                   | Transportar jobs                              |
| Mensageria    | RabbitMQ                                                       | Desacoplar a API dos runners; servir de buffer em picos; rotear por linguagem (exchange direct, 1 fila por linguagem); distribuir a carga entre réplicas (competing consumers); retry e DLQ nativos; devolver resultados (`submissions.results`)                                                 | Executar ou isolar código; guardar estado final |
| Runner C/C++  | Container Docker (`runner-cpp/`: g++ + isolate) + worker Java 21 / Spring Boot ([ADR-008](adr/008-runner-java-casos-endpoint-interno.md)) | Consumir `submissions.cpp`; buscar os casos de teste no endpoint interno (com cache); preparar o box; chamar o isolate para compilar e executar; ler o meta-arquivo; **comparar a saída** (normalizando espaços e quebras de linha finais); montar o veredito; publicar o resultado; confirmar (ack) a mensagem; limpar o box                                  | Persistir no banco; falar com o cliente       |
| Sandbox       | **isolate**                                                    | Isolar **cada** compilação/execução: namespaces Linux (sem rede, PIDs e filesystem próprios), cgroups (limites de CPU, memória e nº de processos), limites de tempo (CPU e wall), tamanho máximo de arquivo, diretório de trabalho (box) descartável; relatar tempo, memória, exit code e sinal no meta-arquivo | Decidir o veredito; comparar a saída          |

## 3.5 Escalabilidade

A escalabilidade é desenhada para N linguagens, mas implementada inicialmente apenas para C/C++.

- **Horizontal por linguagem:** mais réplicas do `runner-cpp` consumindo a mesma fila `submissions.cpp` (competing consumers), **sem limite de partições**.
- **Vertical por réplica:** cada runner processa até N soluções em paralelo, usando `--box-id` distintos do isolate (um box por execução simultânea). O `prefetch` do consumidor é igual a N.
- **Novas linguagens:** nova imagem + fila `submissions.<lang>` ligada ao exchange `juizin.submissions`. A API só precisa conhecer o mapeamento linguagem → fila (entidade `Linguagem`, ver 5.1), sem alterar o código do fluxo.
- **Resultados:** uma única fila `submissions.results`, consumida pela API.
- **Caminho de escala:** um nó (MVP) → cluster de 3 nós com filas quorum e sharding das filas mais carregadas via consistent-hash exchange (regional) → brokers regionais com Federation/Shovel (nacional). O gargalo esperado é a CPU dos runners, não o broker. Detalhes e gatilhos para reavaliar o Kafka no [ADR-006](adr/006-rabbitmq-mensageria.md).

## 3.6 Riscos e restrições conhecidas

- O isolate exige que o container do runner rode **privilegiado / com acesso a cgroups v2** (ver [ADR-005](adr/005-runner-container-privilegiado.md)).
- **A compilação também roda no sandbox**: o g++ pode ser abusado (ex.: `#include "/dev/random"`, binários enormes).
- **O RabbitMQ é at-least-once** (a mensagem é reentregue se não houver ack): o processamento deve ser idempotente pelo `solucaoId`.
- **Dual write DB + RabbitMQ**: publicação após o commit + job de reconciliação ([ADR-007](adr/007-publicacao-apos-commit-reconciliacao.md)).
- **Sem replay**: depois do ack, a mensagem deixa de existir. O PostgreSQL é a fonte da verdade.
- Execuções longas não podem passar do `consumer_timeout` do RabbitMQ (padrão de 30 min).
- Medir tempo de **CPU** (não wall) e fixar CPU por box para evitar o efeito de vizinho barulhento.
- A mensagem leva o código e os IDs dos casos de teste. O runner busca os casos no endpoint interno da API, com o token de serviço, e os guarda em cache (ver 3.3.1).

## 3.7 Decisões em aberto

- Estados e vereditos da solução → seções 4.1 e 5.3.
- Modelo de domínio → seção 5.
- Decisões técnicas registradas como ADRs → seção 6 e [docs/adr](adr/README.md).
- Linguagem de implementação do worker do runner → definida: Java 21 / Spring Boot ([ADR-008](adr/008-runner-java-casos-endpoint-interno.md)).

# 4. Fluxos críticos de negócio

## 4.1 Envio e avaliação de solução

Fluxo técnico detalhado no diagrama 3.3. Regras aplicadas pela API antes de persistir:

- Solução **avulsa** (sem grupo): qualquer usuário autenticado, para qualquer atividade.
- Solução **no grupo**: o autor precisa ser membro, a atividade precisa fazer parte do grupo e o grupo precisa estar `EM_ANDAMENTO`.
- Apenas os casos de teste `OFICIAL` (públicos ou não) participam do veredito.

Ciclo de vida da **Solução**:

```mermaid
stateDiagram-v2
    [*] --> PENDENTE: enviada
    PENDENTE --> EM_EXECUCAO: runner consumiu
    PENDENTE --> FALHA: tentativas esgotadas (DLQ)
    EM_EXECUCAO --> CONCLUIDA: veredito calculado
    EM_EXECUCAO --> FALHA: erro de infraestrutura
    CONCLUIDA --> [*]
    FALHA --> [*]
```

## 4.2 Ciclo de vida do Grupo de Atividade

As transições acontecem automaticamente por `dataInicio`/`dataFim` (job agendado) ou manualmente por um **Avaliador**. Não há volta de estado.

```mermaid
stateDiagram-v2
    [*] --> AGUARDANDO_INICIO: grupo criado
    AGUARDANDO_INICIO --> EM_ANDAMENTO: dataInicio atingida / Avaliador inicia
    EM_ANDAMENTO --> FINALIZADO: dataFim atingida / Avaliador finaliza
    FINALIZADO --> [*]
```

O que cada estado permite:

| Estado              | Avaliador                                                                                                   | Estudante                                                                   |
|:--------------------|:------------------------------------------------------------------------------------------------------------|:----------------------------------------------------------------------------|
| `AGUARDANDO_INICIO` | adiciona/remove atividades e define o peso de cada uma; compartilha o convite; remove estudantes            | vê as atividades e os participantes; compartilha o convite                  |
| `EM_ANDAMENTO`      | acompanha os envios de todos (tentativa, veredito, download do código) e o placar                           | envia soluções, vê os próprios envios e o placar                            |
| `FINALIZADO`        | consolida os envios por estudante e atividade (peso, tentativas, sucesso, pontos) e exporta CSV             | vê a própria pontuação por atividade                                        |

Atividades, pesos e participantes ficam congelados a partir de `EM_ANDAMENTO`.

## 4.3 Gestão de membros

1. O **Usuário** cria o grupo: ele vira **Avaliador** e o `codigoConvite` é gerado (uma única vez).
2. Qualquer membro compartilha o link de convite, que aciona `POST /api/groups/join/{codigoConvite}`.
3. Um usuário autenticado acessa o link e entra como **Estudante**. Se já for membro, nada muda; se o grupo já tiver iniciado (`EM_ANDAMENTO` ou `FINALIZADO`), a entrada é recusada.
4. Um **Avaliador** pode promover um membro a **Avaliador**.
5. Antes do início, um **Avaliador** pode remover **Estudantes** (`DELETE /api/groups/{id}/members/{userId}`). Como o convite não é revogável, quem tiver o link pode entrar de novo enquanto o grupo não iniciar.

## 4.4 Pontuação e placar

- **Tentativas**: todos os envios do estudante para a atividade, dentro do grupo.
- **Sucesso**: a atividade conta como aceita se houver pelo menos um envio `ACEITA`. Aceites repetidos na mesma atividade não somam.
- **Pontos**: uma atividade aceita vale o seu peso (1 a 100, padrão 1); senão, 0. A pontuação máxima é a soma dos pesos.
- O placar (`GET /api/groups/{id}/progress`) mostra a todos os membros quantas atividades cada estudante teve aceitas. O relatório (`GET /api/groups/{id}/report`) traz tentativas, sucesso e pontos por atividade: todos os estudantes para o **Avaliador**, só a própria linha para o **Estudante**.
- Entram só os estudantes atuais e as atividades que ainda fazem parte do grupo.

# 5. Modelo de dados de domínio

## 5.1 Entidades

| Entidade                 | Descrição                                                  | Atributos principais                                                                                                  |
|:-------------------------|:-----------------------------------------------------------|:----------------------------------------------------------------------------------------------------------------------|
| **Usuario**              | Usuário da aplicação (login e senha)                       | id, nome, login, senhaHash, criadoEm                                                                                   |
| **Atividade**            | Enunciado do exercício; criada por qualquer usuário        | id, autor, titulo, enunciado, limiteTempoMs, limiteMemoriaMb                                                           |
| **CasoDeTeste**          | Entrada e saída esperada de uma atividade                  | id, atividade, autor, entrada, saidaEsperada, tipo (`OFICIAL` / `COMUNITARIO`), grupo (só para os comunitários), publico (só para os oficiais) |
| **Linguagem**            | Linguagem suportada e o roteamento para o runner (ver 3.5) | id, nome, versao, fila (ex.: `submissions.cpp`), extensao (ex.: `cpp`), ativa                                          |
| **Solucao**              | Código enviado por um usuário para uma atividade           | id, autor, atividade, grupo (opcional), linguagem, codigo, status, veredito, tempoMs, memoriaKb, enviadoEm, publicadaEm |
| **ResultadoCasoDeTeste** | Resultado de cada caso de teste oficial executado          | id, solucao, casoDeTeste, veredito, tempoMs, memoriaKb                                                                 |
| **GrupoAtividade**       | Compilado de atividades com ciclo de vida                  | id, criador, nome, descricao, status, dataInicio, dataFim, codigoConvite                                               |
| **GrupoAtividadeItem**   | Associação N:N entre grupo e atividade                     | grupo, atividade, ordem, peso (1 a 100, padrão 1: pontos de um envio aceito)                                           |
| **MembroGrupo**          | Participação de um usuário em um grupo                     | grupo, usuario, papel (`AVALIADOR` / `ESTUDANTE`), entrouEm                                                            |

> **Convite:** é um link único por grupo (`codigoConvite`), gerado uma única vez na criação do grupo. Acessar o link resulta na criação de um `MembroGrupo` com papel `ESTUDANTE`.

## 5.2 Diagrama ER

```mermaid
erDiagram
    USUARIO ||--o{ ATIVIDADE : "cria"
    USUARIO ||--o{ SOLUCAO : "envia"
    USUARIO ||--o{ MEMBRO_GRUPO : "participa"
    ATIVIDADE ||--|{ CASO_DE_TESTE : "possui"
    ATIVIDADE ||--o{ SOLUCAO : "recebe"
    ATIVIDADE ||--o{ GRUPO_ATIVIDADE_ITEM : "compõe"
    GRUPO_ATIVIDADE ||--o{ GRUPO_ATIVIDADE_ITEM : "contém"
    GRUPO_ATIVIDADE ||--|{ MEMBRO_GRUPO : "tem"
    GRUPO_ATIVIDADE |o--o{ SOLUCAO : "contexto (opcional)"
    LINGUAGEM ||--o{ SOLUCAO : "escrita em"
    SOLUCAO ||--o{ RESULTADO_CASO_DE_TESTE : "gera"
    CASO_DE_TESTE ||--o{ RESULTADO_CASO_DE_TESTE : "avaliado em"

    USUARIO {
        uuid id PK
        string nome
        string login UK
        string senhaHash
        timestamp criadoEm
    }
    ATIVIDADE {
        uuid id PK
        uuid autorId FK
        string titulo
        text enunciado
        int limiteTempoMs
        int limiteMemoriaMb
    }
    CASO_DE_TESTE {
        uuid id PK
        uuid atividadeId FK
        uuid autorId FK
        uuid grupoId FK "só COMUNITARIO"
        text entrada
        text saidaEsperada
        enum tipo "OFICIAL | COMUNITARIO"
        boolean publico "só OFICIAL"
    }
    LINGUAGEM {
        uuid id PK
        string nome
        string versao
        string fila "ex.: submissions.cpp"
        string extensao "ex.: cpp"
        boolean ativa
    }
    SOLUCAO {
        uuid id PK
        uuid autorId FK
        uuid atividadeId FK
        uuid grupoId FK "opcional"
        uuid linguagemId FK
        text codigo
        enum status
        enum veredito
        int tempoMs
        int memoriaKb
        timestamp enviadoEm
        timestamp publicadaEm "nulo = ainda não publicada"
    }
    RESULTADO_CASO_DE_TESTE {
        uuid id PK
        uuid solucaoId FK
        uuid casoDeTesteId FK
        enum veredito
        int tempoMs
        int memoriaKb
    }
    GRUPO_ATIVIDADE {
        uuid id PK
        uuid criadorId FK
        string nome
        text descricao
        enum status
        timestamp dataInicio
        timestamp dataFim
        string codigoConvite UK
    }
    GRUPO_ATIVIDADE_ITEM {
        uuid id PK
        uuid grupoId FK "UK (grupoId, atividadeId)"
        uuid atividadeId FK
        int ordem
        int peso "1 a 100, padrão 1"
    }
    MEMBRO_GRUPO {
        uuid id PK
        uuid grupoId FK "UK (grupoId, usuarioId)"
        uuid usuarioId FK
        enum papel "AVALIADOR | ESTUDANTE"
        timestamp entrouEm
    }
```

## 5.3 Enums

- `StatusGrupo`: `AGUARDANDO_INICIO`, `EM_ANDAMENTO`, `FINALIZADO`
- `PapelGrupo`: `AVALIADOR`, `ESTUDANTE`
- `StatusSolucao`: `PENDENTE`, `EM_EXECUCAO`, `CONCLUIDA`, `FALHA`
- `Veredito`: `ACEITA`, `RESPOSTA_ERRADA`, `TEMPO_EXCEDIDO`, `MEMORIA_EXCEDIDA`, `ERRO_EXECUCAO`, `ERRO_COMPILACAO`, `ERRO_INTERNO`
- `TipoCasoDeTeste`: `OFICIAL`, `COMUNITARIO`

## 5.4 Regras de domínio

- Qualquer usuário cria atividades e envia soluções para qualquer atividade, com ou sem grupo.
- Uma atividade pode fazer parte de vários grupos (N:N).
- Quem cria o grupo vira automaticamente `AVALIADOR`. Um grupo tem sempre pelo menos 1 `AVALIADOR`.
- Só o `AVALIADOR` promove membros a `AVALIADOR`, edita o grupo, adiciona ou remove atividades, define pesos, remove estudantes e inicia ou finaliza o grupo manualmente.
- Atividades, pesos e participantes só mudam com o grupo em `AGUARDANDO_INICIO` (adicionar/remover atividade, alterar peso, entrar pelo convite e remover membro são recusados depois disso).
- Só membros `ESTUDANTE` podem ser removidos; assim o grupo nunca fica sem `AVALIADOR`.
- Convite: qualquer membro pode compartilhar o link do grupo. Ao acessá-lo autenticado, o usuário entra como `ESTUDANTE`. Se já for membro, nada muda (operação idempotente). Só é possível entrar antes do início do grupo.
- Pontuação: cada atividade aceita vale o seu peso, uma única vez por estudante (ver 4.4).
- Uma solução com `grupo` só é aceita se o autor for membro, a atividade fizer parte do grupo e o grupo estiver `EM_ANDAMENTO`.
- Casos `OFICIAL` são criados pelo autor da atividade e **são os únicos usados no veredito**. Casos `COMUNITARIO` são criados por membros de um grupo e só ficam visíveis para os membros desse grupo.
- Um caso `OFICIAL` pode ser marcado como **público**, na criação ou depois, só pelo autor da atividade: continua valendo no veredito e aparece como exemplo para qualquer pessoa, inclusive sem login. Casos `COMUNITARIO` nunca são públicos.
- Na tela da atividade, o membro vê os casos `COMUNITARIO` daquela atividade de todos os grupos de que participa, cada um identificado pelo grupo de origem.
- Visibilidade de soluções: o autor vê as próprias; o `AVALIADOR` vê todas as soluções do seu grupo, inclusive o código (`GET /api/submissions/{id}/code`, que devolve também a extensão da linguagem para o download).

# 6. Decisões técnicas e restrições de segurança

- Senhas armazenadas apenas como hash (BCrypt).
- Leitura pública: `GET /api/activities`, `GET /api/activities/{id}`, `GET /api/activities/{id}/test-cases/public` (só os casos oficiais marcados como públicos) e `GET /api/languages` não exigem login (catálogo para visitantes). Todo o resto exige JWT, inclusive os demais casos de teste.
- Endpoints internos (`/api/internal/**`, hoje só `GET /api/internal/activities/{id}/test-cases`): usados pelo runner e autenticados por um token de serviço (`juizin.runner.token`, variável `JUIZIN_RUNNER_TOKEN`), numa chain própria do Spring Security, e não pelo JWT dos usuários.
- As autorizações por papel no grupo são verificadas no `service`, não só no frontend.
- O `codigoConvite` deve ser um token aleatório e não previsível (ex.: 128 bits, URL-safe). Como ele não é regenerado, um link vazado não pode ser revogado: **risco aceito por enquanto**.
- Decisões de arquitetura registradas como ADRs em [docs/adr](adr/README.md):
    - [ADR-001](adr/001-kafka-mensageria.md): Kafka como mensageria entre API e runners (Substituída pelo ADR-006)
    - [ADR-002](adr/002-isolate-sandbox.md): isolate como sandbox de cada execução (Aceita)
    - [ADR-003](adr/003-sse-notificacao-resultado.md): SSE para notificar o resultado ao cliente (Aceita; implementado no SSE do grupo e no SSE da solução)
    - [ADR-004](adr/004-transactional-outbox.md): Transactional Outbox para publicar soluções no Kafka (Substituída pelo ADR-007)
    - [ADR-005](adr/005-runner-container-privilegiado.md): runner em container privilegiado com cgroups v2 (Aceita)
    - [ADR-006](adr/006-rabbitmq-mensageria.md): RabbitMQ como mensageria entre API e runners (Aceita)
    - [ADR-007](adr/007-publicacao-apos-commit-reconciliacao.md): publicação após o commit com job de reconciliação (Aceita)
    - [ADR-008](adr/008-runner-java-casos-endpoint-interno.md): worker do runner em Java/Spring Boot, com os casos de teste lidos de um endpoint interno (Aceita)