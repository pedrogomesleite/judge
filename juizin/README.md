# Juizin — frontend

Vue 3 (Composition API com `<script setup lang="ts">`), Vite, Vue Router, Pinia e PrimeVue 4 (tema Aura).

## Rodando

Requisitos: Node `^22.18` ou `>=24.12` e a API rodando em `http://localhost:8080` (pelo IntelliJ).

```sh
npm install
npm run dev        # http://localhost:5173
```

O Vite encaminha `/api` para a API, então o backend não precisa de CORS. Para apontar para outra URL, use a variável `JUIZIN_API_URL`, por exemplo `JUIZIN_API_URL=http://localhost:9090 npm run dev`.

```sh
npm run build      # type-check (vue-tsc) + build de produção em dist/
```

## Telas

| Rota               | Acesso      | Tela                                                                 |
|:-------------------|:------------|:---------------------------------------------------------------------|
| `/login`           | público     | Login                                                                |
| `/signup`          | público     | Criar conta (entra automaticamente)                                  |
| `/activities`      | público     | Atividades avulsas (catálogo)                                        |
| `/activities/:id`  | público     | Detalhe da atividade, casos de teste e envio da resposta (`?groupId=` envia no grupo) |
| `/groups`          | autenticado | Meus grupos, separados entre avaliador e estudante                   |
| `/groups/:id`      | autenticado | Tela do grupo, conforme o papel e o status do grupo, atualizada em tempo real |
| `/groups/join/:inviteCode` | autenticado | Aceita o link de convite e leva ao grupo                     |
| `/submissions`     | autenticado | Minhas respostas, separadas entre sucessos e erros                   |

Na tela de detalhe, um visitante pode escrever o código, mas precisa entrar para enviar. O rascunho fica guardado durante o login.

A mesma tela mostra os **exemplos** (casos oficiais que o autor marcou como públicos, visíveis sem login) e, para quem está logado, os **casos comunitários** compartilhados em todos os grupos de que a pessoa participa, separados pelo grupo de origem. O grupo do `?groupId=` aparece primeiro e, com ele, o botão para compartilhar um caso nesse grupo.

O autor da atividade vê também o cartão **Casos oficiais**, onde cria casos e marca ou desmarca cada um como público.

Em **Meus grupos**, o botão **Novo grupo** cria um grupo com nome, descrição e, opcionalmente, datas de início e fim (com elas, o grupo inicia e finaliza sozinho). Quem cria entra como **avaliador** e é levado à tela do grupo.

### Tela do grupo (`/groups/:id`)

O conteúdo depende do papel no grupo e do status:

| Status              | Avaliador                                                                                                                              | Estudante                                                                         |
|:--------------------|:---------------------------------------------------------------------------------------------------------------------------------------|:----------------------------------------------------------------------------------|
| Aguardando início   | adiciona/remove atividades do catálogo e define o peso de cada uma (1 a 100, padrão 1); link de convite; participantes, com opção de remover estudantes; botão **Iniciar agora** | atividades (com peso) e participantes, só leitura; link de convite               |
| Em andamento        | envios por atividade (tentativa, retorno, ver/baixar código); placar de atividades aceitas por estudante; botão **Finalizar agora**     | atividades com os próprios envios e o link para enviar; placar dos participantes |
| Finalizado          | resultado por estudante e por atividade (peso, tentativas, sucesso, pontos) e **Exportar CSV**                                          | pontuação por atividade e total                                                   |

Regras: uma atividade aceita vale o seu peso, uma única vez por estudante (aceites repetidos não somam). Atividades, pesos e participantes só mudam antes do início; o link de convite só aparece nesse estado, porque depois a entrada é recusada. O CSV usa `;` e BOM UTF-8 para abrir direto no Excel em português.

A tela ouve o SSE do grupo (`useEventosDoGrupo`): cada evento (`GRUPO`, `ATIVIDADES`, `MEMBROS`, `SOLUCOES`) faz a tela buscar de novo só o que mudou. O indicador **Ao vivo** mostra a conexão; se ela cair, a tela reconecta e recarrega tudo.

Quem abre o link de convite sem sessão passa pelo login (ou cadastro) e volta para o convite; ao aceitar, entra como estudante e cai na tela do grupo. Se já for membro, só é levado ao grupo.

## Estrutura

- `src/api/`: cliente HTTP (`http.ts`) e um módulo por recurso, espelhando a coleção do Bruno (`../bruno`). Os tipos em `types.ts` espelham os DTOs da API.
- `src/stores/auth.ts`: sessão (JWT em `localStorage`) e usuário logado.
- `src/router/index.ts`: rotas e guarda de autenticação (`meta.requiresAuth` / `meta.guestOnly`).
- `src/views/`: telas. `src/components/`: componentes compartilhados.
- `src/composables/useEventosDoGrupo.ts`: SSE do grupo (ADR-003) via `fetch` em streaming (`abrirStream` em `http.ts`), porque o `EventSource` não envia o JWT.
- `src/composables/usePolling.ts`: acompanha o status de uma solução fora da tela do grupo, enquanto não há SSE por solução.

## Por que PrimeVue 4

A partir da versão 5, o PrimeVue deixou de ser MIT e passou a exigir chave de licença. O projeto fica na linha 4.x (`v4-stable`, MIT) com `@primeuix/themes` 2.x e `primeicons` 7. Não atualize para a 5 sem rever a licença.
