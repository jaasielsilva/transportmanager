# Arquitetura do Projeto — transportmanager

> **Molde do kit** — copie para `docs/ARQUITETURA.md` em cada projeto novo e substitua `{placeholders}`.

> Documento de referência obrigatório para todos os desenvolvedores (humanos e IA).
> Siga este padrão em **todos** os módulos e features do projeto.

---

## Stack (fixa do padrão)

| Camada | Tecnologia | Status |
|--------|------------|--------|
| Backend | Java 21 + Spring Boot 3.x | ✅ (molde do kit) |
| Frontend | Angular 22 (standalone + signals, **zoneless**) | ✅ (molde do kit) |
| Banco | MySQL 8 | 🔲 |
| ORM | Spring Data JPA (Hibernate) | 🔲 |
| Migrations | Flyway | 🔲 |
| Segurança | Spring Security + JWT (access + refresh) + RBAC | 🔲 |
| Observabilidade | Spring Actuator + SLF4J/Logback | 🔲 |
| Container | Docker multi-stage + Docker Compose | 🔲 |
| Docs API | springdoc-openapi (Swagger UI) | 🔲 |
| Testes Back | JUnit 5 + Mockito (+ Testcontainers no teste de isolamento) | ✅ |
| Testes Front | Vitest + jsdom (`ng test`, builder oficial) | ✅ |

> A stack é **fixa** em todos os projetos. Não trocar tecnologia sem atualizar este doc e a skill.

---

## Estrutura de Pastas

Layout fixo do repositório — `backend/` e `frontend/` na raiz. O CI e os Dockerfiles dependem disso:

```
{repo}/
├── backend/
├── frontend/
├── docs/                    # ARQUITETURA.md, skills/, adr/
├── scripts/                 # setup-vps.sh, backup.sh, sync-skill.ps1
├── docker-compose.yml       # ambiente local (banco + mailpit)
├── docker-compose.vps.yml   # produção/homologação
├── .env.example             # versionado; o .env real nunca
└── CLAUDE.md
```

### Backend

```
backend/
├── pom.xml
├── Dockerfile
├── src/
│   ├── main/
│   │   ├── java/com/jaasielsilva/transportmanager/
│   │   │   ├── {NomeProjeto}Application.java   # entrypoint
│   │   │   ├── config/                # SecurityConfig, CorsConfig, OpenApiConfig
│   │   │   │   └── tenant/            # TenantContext, TenantConfig, TenantFilter
│   │   │   ├── common/                # ApiResponse, PageResponse, utilitários
│   │   │   ├── exception/             # GlobalExceptionHandler + exceções de domínio
│   │   │   └── features/
│   │   │       ├── auth/              # login, refresh, usuários, perfis
│   │   │       ├── platform/          # PLATFORM_ADMIN: tenants, planos, métricas
│   │   │       ├── billing/           # assinatura, webhooks, dunning
│   │   │       └── carga/     # módulo de referência CRUD
│   │   │           ├── entity/
│   │   │           ├── repository/
│   │   │           ├── dto/
│   │   │           ├── mapper/
│   │   │           ├── service/impl/
│   │   │           └── controller/
│   │   └── resources/
│   │       ├── application.yml        # perfis dev/homolog/prod no mesmo arquivo
│   │       └── db/migration/          # V1__init.sql (baseline do kit), V2__…
│   └── test/
└── (moldes prontos no kit: ApiResponse, PageResponse, GlobalExceptionHandler,
   TenantContext, TenantConfig, application.yml, V1__init.sql)
```

### Frontend

```
frontend/
├── Dockerfile
├── nginx.conf              # serve o SPA + proxy /api → backend:8080 (molde no kit)
├── angular.json · tsconfig*.json · package.json
├── src/
│   ├── styles.css                     # Design System: tokens em :root + classes base
│   ├── app/
│   │   ├── app.ts / app.config.ts / app.routes.ts
│   │   ├── core/                      # infra — nunca regra de negócio
│   │   │   ├── layout/app-shell/      # shell autenticado (menu, topo, banner de cobrança)
│   │   │   ├── navigation/            # nav.config.ts (menu por módulo e papel)
│   │   │   ├── guards/                # authGuard, roleGuard, moduloGuard, assinaturaGuard
│   │   │   ├── interceptors/          # auth.interceptor.ts, error.interceptor.ts
│   │   │   ├── models/                # ApiResponse, PageResponse, UsuarioLogado
│   │   │   └── services/              # AuthService, ToastService
│   │   ├── shared/                    # UI reutilizável e burra
│   │   │   ├── toast/ · confirmacao/ · estado/   (loading, vazio, erro)
│   │   ├── features/
│   │   │   ├── auth/pages/            # login, criar-conta, esqueci-senha, definir-senha
│   │   │   ├── home/                  # checklist de ativação
│   │   │   ├── equipe/                # convite de usuário
│   │   │   ├── billing/               # assinatura (402) e plano (upgrade)
│   │   │   └── carga/         # referência viva
│   │   │       ├── models/ services/ pages/ (lista + form)
│   │   │       └── carga.routes.ts
│   └── environments/                  # environment.ts / environment.prod.ts
```

> Separação fixa: `core/` (infra) vs `shared/` (UI reutilizável) vs `features/` (domínio).

**Zoneless (Angular 20+):** não existe `zone.js`. O que redesenha a tela é **signal** — estado de componente é `signal()`, derivado é `computed()`. Guardar estado num campo comum funciona no `console.log` e não atualiza a view.

---

## Regras Obrigatórias

### Backend

- Pacote raiz: `com.jaasielsilva.transportmanager`
- **Nunca expor Entity na API** — sempre DTOs (Java `record` preferido)
- Validação na entrada: Bean Validation (`@Valid`, `@NotBlank`…) nos DTOs de request
- Respostas padronizadas: `ApiResponse<T>` (seção Contrato de API)
- Logs estruturados via SLF4J — nunca `System.out.println`
- Schema **somente** via Flyway — nunca alterar banco manualmente
- Constructor injection (sem `@Autowired` em field)
- Multi-tenant: filtrar por `empresa_id` do token — nunca do body

### Frontend

- HTTP só em services (`inject(HttpClient)`) — nunca em componente
- Standalone components + `inject()` — sem NgModules
- **Estado de componente em `signal()` / `computed()`** — a aplicação é zoneless; campo comum não redesenha a tela
- Interceptors: um para `Authorization`, um para tratamento global de erro
- URL da API via `environments/` — nunca hardcoded
- Lazy loading por feature em `app.routes.ts`
- Multi-tenant: nunca enviar `empresaId` no body
- Access token **só em memória** (`AuthService`); refresh em cookie httpOnly. `localStorage` com token = XSS vira conta roubada
- Cor, espaçamento e raio sempre por token do `styles.css` — nunca hex no componente

### Banco de Dados

- Migrations somente via Flyway: `V{n}__descricao.sql`
- Tabelas `snake_case` plural; colunas `snake_case`
- Colunas padrão em toda tabela: `id BIGINT AUTO_INCREMENT`, `created_at`, `updated_at`
- Soft delete em dados de negócio: `deleted_at TIMESTAMP NULL` (query padrão filtra `deleted_at IS NULL`)
- FKs explícitas e índices para colunas de busca/filtro
- Multi-tenant: `empresa_id NOT NULL` + índice composto `(empresa_id, …)` em tabelas de negócio
- **`UNIQUE` sempre composto com `empresa_id`** — `UNIQUE(email)` global impede que duas empresas tenham o mesmo contato. É o bug clássico deste padrão e só aparece com o segundo cliente
- **`UNIQUE` + soft delete**: `UNIQUE(empresa_id, email)` impede recriar um registro excluído. Onde isso importar, incluir uma coluna sentinela no índice (ex.: `deleted_seq` que recebe o `id` ao excluir) e documentar a escolha

### Migrations backward-compatible (expand/contract)

Flyway roda na subida e o rollback volta a **imagem anterior**, não o schema. Se a migration for destrutiva, a versão antiga encontra um banco que não entende e o rollback deixa de existir.

| Fase | O que pode |
|------|-----------|
| **Expand** (release N) | `CREATE TABLE`, `ADD COLUMN` **nullable**, novo índice. O código novo escreve nos dois lugares |
| **Migrate** (release N) | Backfill dos dados em migration idempotente |
| **Contract** (release N+1, depois de N estável em produção) | `DROP COLUMN`, `RENAME`, `NOT NULL`, remover FK |

Nunca `DROP` ou `RENAME` na mesma release que introduz o substituto. Coluna nova de tabela grande entra sempre nullable — `ADD COLUMN NOT NULL DEFAULT` reescreve a tabela e trava a aplicação.

---

## Performance e escala

Regras baratas de seguir no começo e caras de corrigir depois:

| Regra | Por quê |
|-------|---------|
| Listagem **sempre** paginada (`size` máx. 100) | `List<T>` cru funciona com 20 registros e derruba a app com 200 mil |
| Índice composto para toda listagem: `(empresa_id, coluna_de_filtro)` | Com `@TenantId` **toda** query já filtra por `empresa_id` — índice que não comece por ele é praticamente inútil |
| Sem N+1: `@EntityGraph` ou `join fetch` em relação usada na listagem | Em dev com 3 registros não aparece; em produção vira timeout |
| `spring.jpa.open-in-view: false` | Impede query no render e conexão presa pelo tempo da resposta |
| Pool Hikari ≈ 10 por instância | Pool grande troca fila na app por fila no banco |
| Rate limit por tenant nas rotas caras (relatório, export, upload) | Banco único: um cliente pesado degrada **todos** os outros |
| Cache só com invalidação definida | Cache multi-tenant sem `empresa_id` na chave vaza dados entre empresas |

### Gatilhos de escala (não antecipar — mas saber o sinal)

| Sinal | Movimento |
|-------|-----------|
| CPU da VPS sustentada > 70% ou p95 acima de 1s | Tirar o MySQL da VPS da aplicação |
| Banco > 20 GB ou RPO menor que 24h | Banco gerenciado com PITR |
| Uma instância não aguenta o pico | 2ª instância do backend + **ShedLock** nos `@Scheduled` (senão o job roda em dobro) e sessão sem estado local |
| Volume de upload crescendo | `FileStorageService` para S3-compatível — volume local não escala nem entra no backup |
| Um tenant grande degradando os demais | Banco dedicado para ele antes de pensar em schema-per-tenant

---

## Contrato de API

### Padrão de URL

```
GET    /api/v1/{recurso}              → listar (paginado, busca `?q=`)
GET    /api/v1/{recurso}/{id}
POST   /api/v1/{recurso}
PUT    /api/v1/{recurso}/{id}
DELETE /api/v1/{recurso}/{id}         → soft delete
```

Ações de domínio: `POST /api/v1/{recurso}/{id}/{acao}` (ex.: `/aprovar`, `/cancelar`)

Plataforma (SaaS): `/api/v1/platform/{recurso}` — só escopo `PLATFORM_ADMIN`

### Envelope de resposta — `ApiResponse<T>`

```json
// Sucesso
{ "success": true, "data": { … }, "message": null }

// Erro (nunca stack trace)
{ "success": false, "data": null, "message": "Cliente não encontrado",
  "errors": [ { "field": "email", "message": "formato inválido" } ] }
```

### Paginação — `PageResponse<T>` (dentro de `data`)

```json
{ "content": [ … ], "page": 0, "size": 20, "totalElements": 134, "totalPages": 7 }
```

Query params: `?page=0&size=20&sort=nome,asc&q=texto`. `size` máximo: 100.

### Códigos HTTP

| Código | Quando |
|--------|--------|
| 200 / 201 | Sucesso / criado (201 com `Location`) |
| 400 | Validação de entrada falhou (`errors[]` preenchido) |
| 401 | Sem token ou token inválido/expirado |
| **402** | **Assinatura em atraso** — somente leitura ou suspensa. Distinto do 403 de propósito: o front leva direto à tela de regularização em vez de mostrar "sem permissão" |
| 403 | Autenticado mas sem permissão |
| 404 | Recurso não existe **ou é de outro tenant** (nunca vazar 403 entre tenants) |
| 409 | Conflito de regra de negócio (ex.: e-mail duplicado) |
| 500 | Erro inesperado — mensagem genérica + log completo com `traceId` |

Tudo tratado no `GlobalExceptionHandler` (`@RestControllerAdvice`).

---

## Autenticação e RBAC

### Estratégia JWT

| Item | Padrão |
|------|--------|
| Access token | JWT, expira em **15 min**, enviado em `Authorization: Bearer` |
| Refresh token | Opaco, expira em **7 dias**, persistido no banco (revogável), rotacionado a cada uso |
| Endpoints | `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout` |
| Senha | BCrypt — nunca logar senha ou token |
| Claims | `sub` (userId), `roles`, `empresaId` |

### Segurança do login (produção desde o dia 1)

- **Tokens no Angular**: refresh token em **cookie httpOnly + Secure + SameSite=Strict** — nunca localStorage (XSS). Access token só em memória (service)
- **Rate limiting** no `/auth/login` (ex.: Bucket4j): limite por IP + e-mail; após 5 falhas seguidas, bloqueio temporário de 15 min + registro em `audit_logs`
- **Esqueci minha senha**: `POST /auth/esqueci-senha` → e-mail com token de uso único, expira em 30 min. Resposta sempre igual — nunca revelar se o e-mail existe
- **Alterar senha (logado)**: `POST /api/v1/me/senha` — mora no `UsuarioController`, não no `AuthController`: `/api/v1/auth/**` é público no `SecurityConfig`, e este endpoint exige sessão (o principal do token é a prova de identidade). Exige a senha atual (conferida antes de qualquer escrita); troca derruba todos os refresh tokens, inclusive o da sessão atual. Sem role exigida — toda role troca a própria senha, `PLATFORM_ADMIN` incluído. Sem esta tela, quem perde o acesso ao e-mail só recupera com `UPDATE` na mão no banco de produção
- **Convite de usuário**: `TENANT_ADMIN` convida por e-mail; token de convite expira em 7 dias; o convidado define a própria senha

### Perfis padrão

| Role | Escopo |
|------|--------|
| `PLATFORM_ADMIN` | Dono do SaaS — todos os tenants, onboarding, planos |
| `TENANT_ADMIN` | Admin da empresa cliente — usuários e config do tenant |
| `USER` | Operacional — telas do dia a dia |
| `{role-extra}` | {adicionar por projeto se precisar} |

- Autorização no backend: `@PreAuthorize("hasRole('…')")` — front só esconde UI
- No Angular: `roleGuard` por rota + diretiva/`@if` para esconder botões

---

## Modelo de domínio

{Descreva entidades, relacionamentos e fluxo do SEU sistema.}

```
{entidade_a}
├── id, …
└── created_at, updated_at, deleted_at

{entidade_b}
├── {entidade_a}_id → FK
└── …
```

### Fluxo de negócio

```
{Etapa 1} → {Etapa 2} → {Etapa 3} → [{Futuro 🔲}]
```

| Etapa | Status | Observação |
|-------|--------|------------|
| {nome} | 🔲 | {nota} |

---

## Multi-tenant (obrigatório — padrão de todo projeto)

**Todo SaaS deste padrão é multi-tenant.** Uma única aplicação e um único banco atendem várias empresas; os dados **nunca** se cruzam.

Tenant = **`empresa_id`**. Isolamento via JWT — **nunca** body/query.

| Camada | Implementação |
|--------|----------------|
| JWT | claim `empresaId`, roles com escopo (`PLATFORM_ADMIN` vs demais) |
| Backend | `TenantContext` (ThreadLocal preenchido por filtro após validar o token) |
| Entity | campo `empresaId` com `@TenantId` — Hibernate filtra **todas** as queries JPA automaticamente |
| Frontend | guard + service de contexto |

### Isolamento automático — `@TenantId` desde o início

Padrão do kit: **não** confiar em filtro manual por query — usar o discriminador nativo do Hibernate (Spring Boot 3.2+):

1. Toda entity de negócio tem `@TenantId private Long empresaId;`
2. Um `CurrentTenantIdentifierResolver` lê o `TenantContext` (preenchido pelo filtro JWT) e entrega o tenant ao Hibernate
3. A partir daí, `findAll()`, JPQL e derived queries já saem filtrados por `empresa_id` — **esquecer o filtro deixa de ser possível**

Moldes prontos no kit: `TenantContext.java` e `TenantConfig.java`.

Regras complementares — **o `@TenantId` não cobre tudo**:

- **Query nativa (`@Query(nativeQuery = true)`) NÃO é filtrada** — evitar; se inevitável, `WHERE empresa_id = :empresaId` manual + atenção redobrada em revisão
- `@TenantId` torna a coluna imutável — correto: registro nunca muda de empresa
- **`UNIQUE` global quebra o isolamento** — sempre `(empresa_id, campo)`, ver Regras Obrigatórias → Banco
- **Limpar o `ThreadLocal` no `finally` do filtro.** O Tomcat reaproveita threads; sem o `clear()` a requisição seguinte herda o tenant da anterior. É vazamento de dados entre empresas, silencioso e intermitente — o pior bug possível neste padrão
- **Teste de isolamento obrigatório** no módulo de referência: criar dados de 2 empresas e provar que uma não lê/edita/exclui a da outra

### Fluxos que legitimamente atravessam tenants

Três casos precisam enxergar todos os tenants: telas do `PLATFORM_ADMIN`, jobs agendados (purge, expiração de trial, dunning) e o processamento do webhook de billing (que chega sem token).

Padrão único: `TenantContext.semFiltroDeTenant(() -> …)`, e **só** dentro de `features/platform/`, `features/billing/` ou de um `@Scheduled`. Nunca dentro de um fluxo de tenant comum — é a única forma de furar o isolamento, então toda ocorrência tem que saltar aos olhos na revisão.

### Impersonação — "entrar como o cliente"

Suporte sem isso é inviável; feito errado é uma porta dos fundos. Desenhar **uma vez**, no início:

- `POST /api/v1/platform/tenants/{id}/impersonar` — exclusivo de `PLATFORM_ADMIN`
- Token de **curta duração** (máx. 30 min), com claim `impersonatedBy` — o backend sabe quem é o humano por trás
- Registro **obrigatório** em `audit_logs` na entrada e na saída
- Banner fixo e visível na UI durante toda a sessão
- **Somente leitura** por padrão; ações financeiras e exclusões sempre bloqueadas

### Fases SaaS

| Fase | Escopo | Status |
|------|--------|--------|
| 1 — Tenant | `empresa_id`, JWT, contexto | ✅ (kit) |
| 2 — Plataforma | Admin global, onboarding de tenants | ✅ (kit) — API e telas |
| 3 — Operacional | Fluxo principal do negócio | 🔲 (do projeto — copiar o carga) |
| 4 — Comercial | Planos, billing | ✅ menos a integração com o gateway 🔲 |

**Billing:** sempre via gateway externo (Stripe, Asaas, Mercado Pago…) — **nunca** armazenar dados de cartão no nosso banco. Guardamos apenas: plano, status da assinatura, ids do gateway.

### Webhooks do gateway (sincronização de assinatura)

- Endpoint público `POST /api/v1/public/webhooks/billing` — **sempre validar a assinatura** do evento (secret do gateway)
- **Idempotência obrigatória**: tabela `webhook_eventos` com id único do evento — evento repetido é reconhecido e ignorado (webhooks reentregam)
- Efeito: atualizar `assinatura_status` da empresa + registrar em `audit_logs`

---

## Ciclo de vida do tenant

| Fase | Padrão |
|------|--------|
| Onboarding | Self-service: `POST /api/v1/public/signup` cria `empresa` + primeiro `TENANT_ADMIN` **numa única transação**, já no plano TRIAL. (Alternativa por projeto: criação manual pelo `PLATFORM_ADMIN`) |
| Convites | Demais usuários entram por convite do `TENANT_ADMIN` (e-mail + token expirável — seção Segurança do login) |
| Suspensão | `assinatura_status != ACTIVE` → bloqueio com tela de regularização; login e billing **sempre** acessíveis |
| Cancelamento | Dados retidos por **{30} dias**; exportação dos dados disponível ao `TENANT_ADMIN` (portabilidade LGPD) |
| Purge | Após a retenção: exclusão definitiva por rotina agendada e documentada + registro em `audit_logs` |

### Ativação — o trial que vira cliente

Um trial que abre numa tela vazia não converte. O padrão trata isso como parte do produto, não como enfeite:

| Peça | Padrão |
|------|--------|
| Dados de demonstração | O signup popula o tenant novo com um conjunto pequeno e realista, marcado como demo e removível num clique. Diferente do seed de `dev` |
| Checklist de onboarding | 3 a 5 passos na home (`convidar usuário`, `cadastrar o primeiro carga`, `concluir o fluxo principal`), com progresso persistido no tenant |
| Momento "aha" | Definir por projeto **qual ação** indica que o cliente entendeu o valor. É a métrica de ativação e ela aparece no painel da plataforma |
| E-mails do trial | D+0 boas-vindas · D+3 se não ativou · D-3 do fim · D+0 do fim |
| Upgrade in-app | O `409` de quota (`LimiteDoPlanoException`) leva para a tela de plano com o limite atingido explícito. Erro de limite é momento comercial, não mensagem de falha |

### Dunning — falha de pagamento

Régua de cobrança de pagamento que falhou. Na maioria das vezes é cartão expirado ou limite estourado — atinge 5% a 10% da base todo mês e **o cliente não sabe que falhou**. Bloquear na hora faz o cliente achar que o produto quebrou; não fazer nada faz você parar de receber sem perceber.

**Implementado no kit** (o gerador já entrega): `ReguaDeDunning` (regra pura e testada), `DunningJob` (agendado, idempotente, com ShedLock), `AssinaturaService` (todas as transições num só lugar), `BillingWebhookController`, `GatewayBilling` (interface — provider trocável) e `AssinaturaAccessInterceptor` (o bloqueio de verdade).

Padrão do kit (em `app.dunning.etapas` — **configurável sem deploy**):

| Momento | Ação |
|---------|------|
| `PAST_DUE` (D+0) | E-mail ao `TENANT_ADMIN` + banner in-app com link de pagamento. Acesso **normal** |
| D+3 | 2º e-mail. Acesso normal |
| D+7 | 3º e-mail; aplicação entra em **somente leitura** — o cliente ainda vê os dados dele |
| D+15 | Suspensão: só login, tela de regularização e billing continuam acessíveis |
| Volta a `ACTIVE` | Acesso restaurado imediatamente, sem intervenção manual |

Os prazos, o nível de acesso e o envio de e-mail de cada etapa vêm da configuração. Prazos fora de ordem crescente **derrubam a aplicação na subida**: régua invertida suspende cliente que está em dia, e é melhor falhar no deploy do que no cliente.

O que **não** é configurável, de propósito: régua diferente por tenant. Com isso ninguém mais responde "quando meus clientes são suspensos?" — a resposta vira "depende", e suporte e teste ficam impossíveis. Exceção individual é o botão **prorrogar** do painel, que é auditado.

Login e pagamento **nunca** são bloqueados — cliente que não consegue entrar não consegue pagar. A lista de rotas isentas no `AssinaturaAccessInterceptor` (`/auth`, `/public`, `/billing`, `/me`, `/platform`) é a parte mais importante da classe: bloquear billing transforma um atraso de cobrança em churn definitivo.

Idempotência da régua: o `UNIQUE (empresa_id, etapa, acao)` em `dunning_eventos`. Não basta comparar `dunning_etapa` — dois processos concorrentes leem o mesmo valor antes de qualquer um gravar. Toda transição registra em `audit_logs` e aparece no painel.

Trial expirado entra na **mesma** régua de quem teve o cartão recusado — o caminho de volta é idêntico.

### Painel do dono do SaaS (`/api/v1/platform`)

Área do dono, não do cliente: enquanto o `TENANT_ADMIN` enxerga a empresa dele, aqui se enxerga a base inteira. Sem isto a operação é feita por `SELECT` manual em produção — funciona com 3 clientes; com 30 você não percebe que 4 estão em `PAST_DUE` há duas semanas.

`@PreAuthorize("hasRole('PLATFORM_ADMIN')")` na **classe**, nunca por método — nenhum endpoint pode escapar por esquecimento.

| Métrica | Por que está no painel |
|---------|------------------------|
| MRR | Só `ACTIVE`. Contar `TRIALING` é a forma mais comum de inflar o próprio número |
| **Receita em risco** | Quanto do MRR está preso no dunning — é o número que faz você abrir o painel hoje |
| Tenants ativos / trial / `PAST_DUE` | Situação da base num relance |
| Trials expirando em 7 dias | Fila de contato comercial |
| Churn do mês | Acima de 5%/mês não se cresce |
| Taxa de ativação | Explica o churn de trial: quem não ativa não converte, e o problema é o onboarding, não o preço |
| Uso por módulo | Módulo que ninguém usa é candidato a corte; o mais usado é argumento de preço |

Ações operacionais: reprocessar webhook que falhou e prorrogar cobrança por N dias (auditada) — ambas existem para que a alternativa não seja `UPDATE` na mão em produção.

**Implementado no kit** (API + telas em `/plataforma`): painel com os números acima, lista de tenants filtrada pelo que exige ação (em atraso, trial vencendo, cancelados), ficha do tenant com histórico da régua e **limite × consumo por quota**, fila de webhooks não processados e o botão de prorrogar.

Consumo de quota é respondido pela própria feature dona do dado, via `ConsumoDeQuota` (`chave()` + `consumoDe(empresaId)`). Feature nova com limite entra no painel sozinha — a plataforma nunca precisa conhecer as tabelas de negócio.

**Ninguém nasce `PLATFORM_ADMIN`**: quem se cadastra vira `TENANT_ADMIN` da própria empresa. A primeira promoção é manual e registrada (LEIA-ME → "O primeiro PLATFORM_ADMIN").

O painel mostra **situação comercial, não conteúdo do tenant**. Para ver o sistema pelos olhos do cliente existe a impersonação, que é auditada.

---

## Planos e liberação de módulos (opcional — apagar se todos os módulos forem liberados para todos)

Modelo comercial padrão: **módulos são liberados por plano de assinatura**. O tenant assina um plano; o plano define quais módulos ele enxerga e pode usar.

### Modelo de dados

```
planos                     # ex.: Básico, Profissional, Completo
├── id, nome, preco_mensal, ativo
└── created_at, updated_at

plano_modulos              # quais módulos cada plano habilita
├── plano_id → FK
└── modulo   # código estável: 'AGENDA', 'FINANCEIRO', 'RELATORIOS'…

empresas                   # tabela do tenant ganha:
├── plano_id → FK
└── assinatura_status      # ACTIVE / PAST_DUE / CANCELED (sincronizado do gateway)
```

- Cada módulo do sistema tem um **código estável** (enum `Modulo` no backend) — é a chave de tudo.
- Trial = plano `TRIAL` com data de expiração; downgrade nunca apaga dados, só bloqueia acesso.

### Enforcement (as 3 camadas)

| Camada | Implementação |
|--------|----------------|
| Backend (obrigatória) | `@RequiresModule(Modulo.X)` no controller (classe ou método) + `ModuloInterceptor`, que valida contra a claim do token. Sem módulo → **403** com mensagem clara. **Implementado no kit** |
| JWT | claim `modules: ["CADASTROS", …]` gerada no login/refresh a partir de `plano_modulos` — mudou de plano, vale no próximo refresh (no máx. 15 min). Lida do token, não do banco: sem consulta extra em todo endpoint |
| Frontend | `nav.config.ts`: cada item declara seu `modulo`; menu só mostra os habilitados. `moduloGuard` na rota leva para `/plano` em vez de dar 403 seco. Front **esconde**, backend **bloqueia**. **Implementado no kit** |

O front lê os módulos de `GET /me` (campo `modulos`), não decodificando o JWT: token é credencial, não fonte de dado de tela.

**Ordem dos interceptors importa** e está fixada no `WebMvcConfig`: assinatura (1) antes de módulo (2). Cliente em atraso recebe **402** com caminho de regularização, em vez de um 403 "módulo não contratado" que o mandaria para a tela de upgrade errada.

`PLATFORM_ADMIN` passa por cima do `@RequiresModule` — opera a plataforma e precisa enxergar tudo para dar suporte. Auth, billing e `/me` **nunca** levam a anotação.

- `assinatura_status != ACTIVE` → acesso somente leitura ou bloqueio com tela de regularização (decidir por projeto).
- Auth e configurações básicas **nunca** ficam atrás de módulo — o cliente sempre consegue entrar e pagar.

### Módulos × planos deste projeto

| Módulo (código) | Básico | Profissional | Completo |
|-----------------|--------|--------------|----------|
| {MODULO_A} | ✅ | ✅ | ✅ |
| {MODULO_B} | — | ✅ | ✅ |
| {MODULO_C} | — | — | ✅ |

> Preencher por projeto. Se um módulo novo nascer, definir em qual plano entra **antes** de codar.

### Quotas por plano (limites de quantidade)

```
plano_limites
├── plano_id → FK
└── chave, valor           # ex.: MAX_USUARIOS=5, MAX_STORAGE_MB=1024
```

- Checagem no **Service** ao criar o recurso; excedeu → **409** com mensagem de upgrade
- Limites e consumo atual visíveis na tela de plano do tenant

---

## Serviços transversais

Padrões para necessidades que todo módulo acaba tendo — sempre atrás de interface, provider trocável.

| Serviço | Padrão |
|---------|--------|
| E-mail transacional | Interface `EmailService`; implementação por provider (SMTP / SendGrid / SES). Envio assíncrono (`@Async`); templates versionados no repo. Usos: convite, esqueci-senha, avisos de billing |
| Upload de arquivos | Interface `FileStorageService`; storage S3-compatível — **nunca** blob no banco. Caminho: `{empresa_id}/{modulo}/{uuid}`. Validar tipo e tamanho no upload; download via URL assinada |
| Jobs agendados | `@Scheduled`; com mais de 1 instância, **ShedLock** para não executar em dobro. Jobs idempotentes, com log de início/fim. Usos: purge de tenants, retry de e-mails, expiração de trial |
| Geolocalização / rotas | Interface `GatewayGeo`; implementação `OpenRouteServiceGeoGateway` (RestClient do spring-web, zero dependência nova) fazendo proxy para a Matrix API do OpenRouteService (plano grátis, sem cartão). **Só o backend conhece a API key** (`OPENROUTESERVICE_API_KEY`, env var) — nunca vai ao bundle do frontend. Falha do upstream vira `GatewayGeoException` → 502. Usos: estimar distância/tempo da rota no form de carga (`POST /cargas/calcular-rota`) |

---

## Deploy — VPS Hostinger + Monitoring (modelo validado na gestao-empresarial)

Toda aplicação sobe do **mesmo jeito**, rápido de configurar via script. Moldes no kit: `setup-vps.sh`, `docker-compose.vps.yml`, `ci.yml`.

### Modelo

| Item | Padrão |
|------|--------|
| Servidor | 1 VPS Hostinger (Ubuntu 22.04) por aplicação; acesso root só no setup, depois usuário `deploy` (chave SSH, sem senha) |
| Estrutura | Produção em `/opt/transportmanager`; homologação em `/opt/transportmanager-homolog` — cada uma com seu `.env` (chmod 600) e seu banco |
| Setup | `bash setup-vps.sh` como root: instala Docker + Nginx + Certbot, cria usuário `deploy`, diretórios, Nginx dos 2 ambientes e `.env` com placeholders `TROCAR` |
| DNS | registros A na Hostinger: `app.transportmanager.erpcorporativo.shop` e `homolog.transportmanager.erpcorporativo.shop` → IP da VPS |
| SSL | Certbot (`--nginx`), renovação automática |
| Imagens | GitHub Container Registry (`ghcr.io/jaasielsilva/transportmanager/backend` e `/frontend`). Tag **imutável** `sha-{commit}` + alias móvel (`latest` prod / `homolog`). O deploy usa o SHA — é isso que torna o rollback possível |
| Deploy | CI: build+push → SSH → grava o novo `IMAGE_TAG` no `.env` → `docker compose pull && up -d` → smoke em `/actuator/health`. Health não sobe → **rollback automático** para a tag anterior e o job falha |
| Rollback manual | `cd /opt/transportmanager && sed -i 's/^IMAGE_TAG=.*/IMAGE_TAG=sha-{commit}/' .env && docker compose up -d` |
| Downtime | `docker compose up -d` derruba e sobe o container: ~30s de indisponibilidade, aceitável neste porte. Blue-green só quando um cliente exigir contratualmente |
| Secrets | Gerados pelo `setup-vps.sh` com `openssl rand` — o `.env` já nasce com valores reais, sem `TROCAR`. GitHub Secrets: `SSH_HOST`, `SSH_USER`, `SSH_KEY` |
| Rede interna | O backend **não** expõe porta. O container do frontend serve o SPA e faz proxy de `/api` e `/actuator` para `backend:8080` (`frontend/nginx.conf`, molde no kit). Quem expõe para fora é só o Nginx da VPS |

### Backup e restore

Backup nunca restaurado não é backup. **Sem restore drill não há go-live.**

| Item | Padrão |
|------|--------|
| Rotina | `scripts/backup.sh` via cron às 03:15 (criado pelo `setup-vps.sh`): `mysqldump --single-transaction` + gzip em `/opt/backups` |
| Retenção | 30 dias, limpeza automática |
| Verificação | O script aborta e falha se o dump sair menor que 10 KB — dump vazio silencioso é o modo de falha mais comum |
| **Off-site** | Backup na mesma VPS não protege contra perda da VPS. `rclone`/`aws s3` no fim do `backup.sh` — **não é opcional em produção** |
| Restore drill | Antes do go-live e a cada 3 meses: restaurar num banco de teste e conferir. Registrar a data no repositório |
| Alerta | `/opt/backups/.ultimo-backup-*` é a marca de vida que dispara o alerta "backup não rodou" |

### Nginx (gerado pelo script)

São **dois** Nginx e é fácil confundir: o da **VPS** (TLS, domínio, firewall) e o de **dentro da imagem do frontend** (SPA + proxy para o backend). O script gera só o primeiro.

- Nasce em **HTTP puro**; o `certbot --nginx --redirect` adiciona o bloco 443 e o redirect. Gerar já com `ssl_certificate` faz o `nginx -t` falhar numa VPS nova — os certificados ainda não existem
- Security headers (`X-Frame-Options`, `nosniff`, `Referrer-Policy`), `client_max_body_size` para uploads
- Containers só em loopback (`127.0.0.1`) — quem expõe é o Nginx
- `/actuator/prometheus` liberado **apenas** para o IP da VPS de monitoring (`allow {IP_MONITORING}; deny all`); **todo o resto de `/actuator/` é negado publicamente** (o smoke test do CI usa `127.0.0.1` dentro do container)

### Integração com o Monitoring central (repo `monitoring`)

| Peça | Como |
|------|------|
| Métricas da app | micrometer-prometheus no backend (snippets prontos em `monitoring/spring-boot-snippets/`) |
| Agentes na VPS | `docker compose -f docker-compose.transportmanager-vps.yml up -d` em `/opt/monitoring-agents`: node-exporter (9100), cadvisor (8091), mysqld-exporter (9104), promtail → tudo `network_mode: host` |
| Firewall | `ufw allow from {IP_MONITORING}` para as portas dos exporters (Docker bypassa ufw — por isso host network) |
| MySQL | bind `127.0.0.1:3306` no compose da app + usuário `exporter` (`.my-transportmanager.cnf`, não versionado) |
| Central | registrar os novos targets no Prometheus do repo `monitoring` + dashboard Grafana + Uptime Kuma nos domínios |

### Alertas — bot Telegram exclusivo por SaaS

- No go-live, criar no **BotFather** um bot **exclusivo deste SaaS** (ex.: `transportmanager_alertas_bot`) + um grupo/chat para receber os alertas
- O **Alertmanager central** (repo `monitoring`) roteia por label `app: transportmanager` → `telegram_configs` com o token do bot deste SaaS
- Token e `chat_id` ficam **somente** no `.env` da stack monitoring — nunca no repo da aplicação
- O que alerta (Produção): app down / health falhando, disco > 85%, surto de 5xx, CPU/memória sustentados, certificado SSL a vencer, backup que não rodou
- Homologação não alerta no Telegram (só dashboard) — alerta de homolog vira ruído e ensina a ignorar o bot

> **Checklist de go-live** (ordem importa — o Certbot precisa do DNS já propagado, e o Nginx é criado só em HTTP justamente para ele conseguir subir antes do certificado existir):
> DNS → `setup-vps.sh` → conferir/completar `.env` (e-mail e billing) → chave pública do Actions em `authorized_keys` → `certbot --nginx --redirect` → GitHub Secrets → `docker login ghcr.io` como `deploy` → 1º deploy manual → agentes de monitoring → registrar no Prometheus central → **bot Telegram do SaaS + rota no Alertmanager** → **restore drill** → push na `dev`/`main` passa a fazer o resto.

---

## Roadmap enterprise (decisão consciente — implementar só com demanda)

| Item | Gatilho para implementar | Status |
|------|--------------------------|--------|
| 2FA (TOTP) | Clientes com dados sensíveis pedirem | 🔲 |
| SSO corporativo (SAML/OIDC) | Primeiro cliente enterprise exigir | 🔲 |
| Métricas + alertas (Micrometer/Prometheus) | Primeiros clientes pagantes em produção | 🔲 |
| White-label por tenant | Demanda comercial | 🔲 |
| Schema-per-tenant | Exigência contratual de isolamento físico | 🔲 |

### Diferenciação — o que tira o produto da comoditização

Um CRUD multi-tenant bem feito é correto, mas hoje é commodity. O que muda a conversa de preço, em ordem de esforço:

| Item | O que é | Cuidado que já fica definido |
|------|---------|------------------------------|
| **Camada de IA por tenant** | Busca em linguagem natural sobre os dados do próprio tenant, resumo de relatório, preenchimento assistido | O contexto do prompt **nunca** pode conter dado de outro tenant; custo limitado por plano (`plano_limites`, ex. `MAX_IA_REQUESTS`); resposta de IA nunca decide sozinha ação financeira |
| **API pública + webhooks para o cliente** | Não é o webhook de billing que recebemos — é o que o cliente consome. Vira upsell de plano e trava a saída | Chave por tenant (revogável), rate limit por chave, versionamento `/api/v1`, `ApiResponse`/`PageResponse` já servem |
| **Exportação de dados** | Já é obrigação LGPD; vira funcionalidade quando é botão na tela do `TENANT_ADMIN` | Assíncrona, com link assinado e expirável |
| **Status page pública** | O Uptime Kuma que já monitora os domínios gera a página | Argumento comercial de baixo custo |
| **i18n / timezone / moeda** | Decidir **agora**: chave de tradução em vez de string literal no template, `Instant` no banco, timezone por tenant | Custa quase nada no começo; retrofit depois é reescrita de toda a UI |

> Fora do roadmap de propósito: microserviços, Kubernetes, event sourcing — complexidade sem retorno neste porte.

---

## Governança de TI

Regras de operação do sistema — valem para todo projeto do padrão.

| Área | Padrão | Onde está |
|------|--------|-----------|
| Gestão de mudança | Toda alteração via branch `feat/`/`fix/` → commit convencional → merge na `main` → pipeline. Nada direto em prod | Convenções de Git |
| Gestão de acesso | RBAC no backend; criação/remoção de usuário só por `TENANT_ADMIN`+; revisar acessos ao trocar função | Autenticação e RBAC |
| Rastreabilidade | `audit_logs` para ações críticas + `traceId` em todo log | Auditoria |
| Dados / LGPD | Dados sensíveis mascarados na UI, nunca em log; soft delete; exclusão definitiva só por rotina documentada | Regras Obrigatórias |
| Backup e recuperação | `scripts/backup.sh` diário + retenção 30 dias + cópia off-site; restore drill antes do go-live e a cada 3 meses | Deploy → Backup e restore |
| Continuidade | Health check monitorado; incidente = registrar causa raiz e correção em `docs/adr/` ou em post-mortem no repositório | Infraestrutura |
| Decisões de arquitetura | Toda decisão estrutural (trocar gateway, mudar isolamento de tenant, adotar cache) vira um ADR datado em `docs/adr/` | `docs/adr/` |
| Documentação | `ARQUITETURA.md` + skill são a fonte da verdade; desatualizado = bug. Atualizar ao fechar módulo | Skill seção 11 |
| Segredos | Somente env vars; `gitleaks` no CI barra commit com segredo; rotacionar em caso de vazamento | Ambientes |
| Dependências | `trivy` no CI barra CRITICAL/HIGH com correção disponível | CI |

---

## Auditoria

- Toda tabela: `created_at`, `updated_at` (via `@CreationTimestamp` / `@UpdateTimestamp` ou auditing do JPA)
- Ações críticas (login, exclusão, mudança de permissão, ações financeiras): tabela `audit_logs` — `id, user_id, empresa_id, acao, entidade, entidade_id, detalhes JSON, created_at`
- LGPD: dados sensíveis mascarados na UI e nunca logados

---

## Ambientes e Configuração

| Ambiente | Profile Spring | Branch | Onde | Notas |
|----------|---------------|--------|------|-------|
| Local | `dev` | qualquer | máquina do dev (Docker Compose) | seeds de demo carregados |
| **Homologação** | `homolog` | `dev` | VPS — `homolog.transportmanager.erpcorporativo.shop` | deploy automático a cada push na `dev`; dados de teste |
| **Produção** | `prod` | `main` | VPS — `app.transportmanager.erpcorporativo.shop` | deploy automático a cada push na `main`; `.env` e banco próprios |

> A partir do go-live, **sempre dois ambientes no ar**: nada chega à `main` sem ter sido validado em homologação.

- Secrets **somente** via env vars / `.env` (que fica no `.gitignore`) — nunca commitados
- `OPENROUTESERVICE_API_KEY` (Matrix API, plano grátis sem cartão) — **só no backend**; sem ela o `calcular-rota` responde 502 de propósito (fail-fast). Nunca colocar a key no `environment.ts` do frontend
- Seeds de dev: migration separada `R__seed_dev.sql` ou `CommandLineRunner` condicionado ao profile `dev`
- Frontend: `environment.ts` (dev) / `environment.prod.ts`

---

## Convenções de Git

- Branches fixas: **`main` = Produção** e **`dev` = Homologação** — ambas com deploy automático
- Trabalho: `feat/{modulo}` / `fix/{descricao}` criadas a partir da `dev` → merge na `dev` → validar em homologação → merge `dev` → `main` (go-live da entrega)
- Hotfix urgente: `fix/*` a partir da `main` → merge na `main` **e** na `dev` (para não regredir)
- Commits: Conventional Commits — `feat:`, `fix:`, `refactor:`, `docs:`, `test:`, `chore:`
- Nunca commitar: `.env`, secrets, `node_modules/`, `target/`

---

## Módulos do Projeto

| Módulo | Código (plano) | Backend | Frontend | Tenant |
|--------|----------------|---------|----------|--------|
| Setup base | — (sempre) | ✅ | ✅ | — |
| Autenticação (login + refresh + RBAC) | — (sempre) | ✅ | ✅ | — |
| Ciclo comercial (dunning, planos, `/platform`) | — (sempre) | ✅ | ✅ (banner, `/assinatura`, `/plano`) | ✅ |
| carga (referência) | CADASTROS | ✅ | ✅ | ✅ |
| {próximo módulo} | {MODULO_B} | 🔲 | 🔲 | — |

> Atualize ao fechar cada módulo. DoD: skill `/transportmanager` seção 9.

---

## Infraestrutura

| Item | Status |
|------|--------|
| Docker / Compose (app + banco) | ✅ (kit) |
| Config produção (profile `prod`) | ✅ (kit) |
| Health check (`/actuator/health`) | ✅ (kit) |
| traceId / correlation ID nos logs | ✅ (kit) |
| OpenAPI / Swagger UI | ✅ (kit) |
| CI — `.github/workflows/ci.yml` (molde no kit) | ✅ (kit) |
| Backup diário + **restore drill executado** | 🔲 (só no go-live) |
| Rollback testado (subir a tag `sha-` anterior) | 🔲 (só no go-live) |
| Teste de isolamento entre 2 tenants | ✅ `CargaIsolamentoTenantTest` (kit) |

> O teste de isolamento exige Docker (Testcontainers + MySQL real). **Sem Docker ele é pulado, não falha** — `mvn test` na máquina do dev não pode depender do Docker Desktop estar aberto. No CI o Docker existe sempre, e é lá que ele vale.

---

## Padrão Empresarial

Skill mestre: [`docs/skills/transportmanager/SKILL.md`](./skills/transportmanager/SKILL.md) — invocar com `/transportmanager` (Cursor ou Claude Code).
