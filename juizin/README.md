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
| `/activities/:id`  | público     | Detalhe da atividade e envio da resposta (`?groupId=` envia no grupo) |
| `/groups`          | autenticado | Meus grupos, separados entre avaliador e estudante                   |
| `/submissions`     | autenticado | Minhas respostas, separadas entre sucessos e erros                   |

Na tela de detalhe, um visitante pode escrever o código, mas precisa entrar para enviar. O rascunho fica guardado durante o login.

## Estrutura

- `src/api/`: cliente HTTP (`http.ts`) e um módulo por recurso, espelhando a coleção do Bruno (`../bruno`). Os tipos em `types.ts` espelham os DTOs da API.
- `src/stores/auth.ts`: sessão (JWT em `localStorage`) e usuário logado.
- `src/router/index.ts`: rotas e guarda de autenticação (`meta.requiresAuth` / `meta.guestOnly`).
- `src/views/`: telas. `src/components/`: componentes compartilhados.
- `src/composables/usePolling.ts`: acompanha o status das soluções enquanto a notificação via SSE é TODO (ADR-003).

## Por que PrimeVue 4

A partir da versão 5, o PrimeVue deixou de ser MIT e passou a exigir chave de licença. O projeto fica na linha 4.x (`v4-stable`, MIT) com `@primeuix/themes` 2.x e `primeicons` 7. Não atualize para a 5 sem rever a licença.
