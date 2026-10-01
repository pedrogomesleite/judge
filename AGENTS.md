# Diretrizes do Projeto - Juizin

## 0. Documentação e Arquitetura

- Consulte obrigatoriamente [docs/architecture.md](docs/architecture.md) para compreender personas, fluxos de negócio, modelo de dados e especificações arquiteturais antes de criar ou modificar regras de domínio.

---
## 1. Visão Geral e Stack

- **Backend:** Java 21, Spring Boot 3.x
- **Build Tool Backend:** Maven
- **Banco de Dados:** PostgreSQL
- **Frontend (planejado em `frontend/`):** Vue 3 (Composition API com `<script setup>`, TypeScript, Vite, Pinia)

---
## 2. Padrões de Arquitetura e Código

### Backend (Java / Spring Boot)
- **Estrutura em camadas:**
    - `controller`: Apenas mapeamento HTTP e validação inicial de entrada (`@Valid`).
    - `service`: Regras de negócio e transações (`@Transactional`).
    - `repository`: Interfaces Spring Data JPA.
    - `dto`: Records Java para Request e Response (evitar expor entidades JPA diretamente na API).
- **Tratamento de Exceções:**
    - Use `@ControllerAdvice` / `@ExceptionHandler` centralizado.
    - Crie exceções de domínio customizadas que herdem de `RuntimeException`.
- **Boas Práticas:**
    - Prefira injeção de dependência por construtor (evite `@Autowired` em campos).
    - Use `Optional<T>` como retorno de buscas e trate adequadamente no service.
    - Use Lombok onde aplicável (`@RequiredArgsConstructor`, `@Getter`).

### Frontend (Vue 3 - quando implementado na subpasta `frontend/`)
- Utilizar exclusivamente **Vue 3** com **Composition API** e `<script setup lang="ts">`.
- Gerenciamento de estado global via **Pinia**.
- Centralizar requisições à API em camada/clientes HTTP específicos (apontando para `/api`).
- Componentização desacoplada e tipagem estrita com TypeScript.

---
## 3. Restrições e Regras Estritas (O que NÃO fazer)

- ❌ Não commitar
- ❌ Não utilizar comandos maven (mvn)
- ❌ Não utilizar `System.out.println` ou `e.printStackTrace()` (use logger via `@Slf4j`).
- ❌ Não alterar contratos públicos de APIs sem justificativa explícita.
- ❌ Não utilizar Options API no Vue (usar sempre Composition API com `<script setup>`).
