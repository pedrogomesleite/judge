# ADR-005: Runner roda em container privilegiado com acesso a cgroups v2

## Status
Aceita (2026-10-02). Consequência direta do [ADR-002](002-isolate-sandbox.md).
Texto atualizado em 2026-10-13 para refletir o [ADR-006](006-rabbitmq-mensageria.md) (RabbitMQ no lugar do Kafka); a decisão não mudou.

## Contexto
O isolate cria namespaces e controla cgroups para isolar e limitar cada execução. Dentro de um container Docker comum essas operações são bloqueadas: faltam capabilities como `CAP_SYS_ADMIN` e não há acesso de escrita à hierarquia de cgroups.

Ao mesmo tempo, um container privilegiado tem acesso amplo ao host. Isso aumenta o impacto caso um código malicioso escape do isolate.

## Decisão
O container do runner roda **privilegiado** (`--privileged`, ou o conjunto mínimo de capabilities e montagens que o isolate exigir) com **cgroups v2** disponível. O risco é reduzido com estas restrições:
- **Apenas o runner** é privilegiado. A API, o banco e o RabbitMQ continuam em containers comuns.
- **Runner sem segredos**: o container não recebe credenciais do banco nem da API e só acessa o RabbitMQ (e a leitura dos casos de teste).
- **Rede restrita**: o runner só alcança o RabbitMQ. O código do usuário não tem rede nenhuma (garantido pelo isolate).
- **Hosts dedicados** para runners em produção (separados da API e do banco), quando a infraestrutura permitir.
- Imagem mínima, processo do runner sem root fora do que o isolate exige e versão do isolate e do kernel mantidas atualizadas.

## Consequências
- ✅ Permite usar o isolate dentro de containers, mantendo o modelo de runners por linguagem do [ADR-006](006-rabbitmq-mensageria.md).
- ⚠️ Um escape do isolate (bug de kernel ou do próprio isolate) daria acesso ao host do runner. Esse **risco residual é aceito** e mitigado pelas restrições acima.
- ⚠️ Exige hosts Linux com cgroups v2 e impede rodar o runner em ambientes que proíbem containers privilegiados (alguns serviços gerenciados).
- ⚠️ Em Kubernetes, exige uma `PodSecurity` permissiva no namespace dos runners.

## Alternativas consideradas
- **Runner direto em VM (sem container)**: elimina o container privilegiado, mas perde a padronização de imagem por linguagem.
- **gVisor / Firecracker como camada externa**: isolamento mais forte em volta do runner, com maior custo e complexidade. Pode ser reavaliado se o Juizin for exposto publicamente em escala.
