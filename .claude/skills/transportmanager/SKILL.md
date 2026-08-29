---
name: transportmanager
description: Padrão empresarial do transportmanager (Java + Spring Boot 3 + Angular 22). Use em QUALQUER desenvolvimento — feature, correção, refactor, deploy ou revisão. Produto vendável, código consistente, pronto para produção.
---

# TransportManager — Padrão Empresarial

> **Molde do kit** — copie para `docs/skills/transportmanager/SKILL.md`, `.cursor/skills/transportmanager/SKILL.md` e `.claude/skills/transportmanager/SKILL.md`. Substitua `{placeholders}`.

Skill mestre do projeto. Siga **sempre** ao desenvolver, revisar ou planejar.

Documento técnico (stack, contrato de API, RBAC, regras): `docs/ARQUITETURA.md` — **leia antes de codar**.

---

## 1. Princípio central

Construir um **produto vendável**, não só CRUDs corretos.

| Pilar | Pergunta-chave |
|-------|----------------|
| **Produto** | Qual dor real isso resolve? |
| **Arquitetura** | Segue o padrão do projeto? |
| **UX** | O usuário consegue usar sem ajuda? |
| **Segurança** | Quem pode fazer o quê? |
| **Deploy** | Sobe em produção sem surpresa? |
| **Observabilidade** | Dá para debugar em prod? |

Se um pilar ficar de fora, a feature **não está pronta**.

> **Docs = alvo + estado real.** 🔲 = ainda não existe no código. **Não inventar o que não existe.**

---

## 2. Estado atual do projeto

**Atualizar ao fechar cada entrega.**

### O que já existe

| Área | Implementado |
|------|----------------|
| Documentação | ARQUITETURA + skill |
| Backend | ✅ base, tenant, auth, billing, `/platform` |
| Frontend | ✅ shell, guards, interceptors, toast, design tokens |
| Auth (login + refresh + RBAC) | ✅ back e front (login, criar conta, esqueci senha, convite, alterar senha logado em `/minha-conta`) — `POST /api/v1/me/senha` mora no `UsuarioController` porque `/api/v1/auth/**` é público, sem role exigida (toda role troca a própria senha), derruba todas as sessões ao trocar |
| carga (CRUD ref) | ✅ back e front (lista + form, quota, soft delete) + **estimativa de rota**: Distance Matrix via `features/geo` (`POST /cargas/calcular-rota`), API key só no backend (`GOOGLE_MAPS_API_KEY`), falha do upstream → 502 |
| Ciclo comercial | ✅ dunning, banner, `/assinatura`, `/plano` |
| Painel do dono (`/plataforma`) | ✅ métricas, tenants, ficha, quotas, webhooks |

### O que ainda não existe (não inventar)

| Área | Pendente |
|------|----------|
| `GatewayBilling` do provedor (Stripe/Asaas/MP) | 🔲 |
| Impersonação ("entrar como o cliente") | 🔲 |
| Listagem/desativação de usuários (só o convite existe) | 🔲 |
| Fase 2 — atribuição de transporte: `Veiculo`/`Motorista`, role `MOTORISTA`, status `ACEITA`, aceite/recusa com motivo | 🔲 |
| Fase 3 — execução: `Ocorrencia` + comprovante de entrega (upload) | 🔲 |
| Fase 4 — rastreamento em tempo real (telemetria, separada da Distance Matrix) | 🔲 |
| {módulo do projeto} | 🔲 |

### Referência viva

- CRUD: `features/carga/` (back e front) — **copie a estrutura dele em toda feature nova**
- Auth: `features/auth/` + `core/`

### Design System — {nome-tema}

| Token | Valor (padrão do kit) | Uso |
|-------|-------|-----|
| `--color-primary` | `#4f46e5` | Botões, marca |
| `--color-accent` | `#0ea5e9` | Links, destaques |
| `--bg-page` | `#f6f7fb` | Fundo |
| `--card-bg` | `#ffffff` | Cards |

Tokens em `styles.css` (`:root`) — componentes usam `var(--…)`, nunca hex direto.
Trocar a marca do projeto = trocar os valores ali, uma vez.

### Próxima entrega

**{primeiro módulo do domínio}** — o setup, o auth e o carga já vêm prontos do kit.

---

## 3. Domínio e priorização

```
{Etapa 1} → {Etapa 2} → {Etapa 3} → {Futuro}
```

Ordem sugerida:

1. Auth + perfis (RBAC)
2. Tenant (`empresa_id` — obrigatório)
3. Cadastros base
4. Fluxo principal
5. Relatórios
6. Deploy (+ backup com restore drill)
7. Billing (gateway externo — nunca cartão no banco) + **dunning**
8. Ativação: dados de demonstração no signup + checklist de onboarding
9. Painel do dono do SaaS (`/platform`) — sem ele a operação é feita no MySQL
10. Integrações

### Antes de codar

1. Quem usa?
2. Qual dor resolve?
3. Como medir sucesso?
4. MVP vs v2?

---

## 4. Fluxo de nova feature

Ordem fixa:

```
BACKEND:  Entity → Migration (Flyway) → Repository → DTOs → Mapper → Service → Controller → Testes
FRONTEND: Model → Service → Pages (list + form) → Rota lazy
```

### Backend (`com.jaasielsilva.transportmanager`)

```
features/{feature}/entity/ repository/ dto/ mapper/ service/impl/ controller/
```

- DTOs (`record`) na API, nunca Entity
- Bean Validation nos DTOs de request (`@Valid` no controller)
- Resposta sempre em `ApiResponse<T>`; listagem em `PageResponse<T>` (ARQUITETURA → Contrato de API)
- Erros de negócio: exceção de domínio → `GlobalExceptionHandler` (nunca try/catch com resposta manual no controller)
- Migration: `V{n}__descricao.sql` — nunca `ddl-auto: update`. **Backward-compatible**: coluna nova sempre nullable; `DROP`/`RENAME` só na release seguinte (ARQUITETURA → expand/contract)
- `UNIQUE` sempre composto com `empresa_id` — nunca global
- Soft delete: `deleted_at`, queries filtram `deleted_at IS NULL`
- Endpoint protegido por `@PreAuthorize` com a role certa
- Multi-tenant (obrigatório): entity com `@TenantId` no campo `empresaId`; tenant vem do token via `TenantContext` — dados de empresas nunca se cruzam. Query nativa só com filtro manual de `empresa_id`
- Cruzar tenants só por `TenantContext.semFiltroDeTenant(...)`, e só em `features/platform/`, `features/billing/` ou `@Scheduled`
- Listagem nova: índice `(empresa_id, coluna_de_filtro)` na migration + `@EntityGraph`/`join fetch` se houver relação (sem N+1)
- Teste de Service (JUnit 5 + Mockito) no mínimo: caso feliz + regra de negócio principal

### Frontend

```
features/{feature}/models/ services/ pages/ {feature}.routes.ts
```

- Standalone components + `inject()` — sem NgModule
- Estado em `signal()`/`computed()` — a app é **zoneless**: campo comum não atualiza a tela
- HttpClient só em services; componentes consomem o service
- Rota lazy em `app.routes.ts` com `authGuard` + `assinaturaGuard` (+ `roleGuard`/`moduloGuard` se restrito)
- Item novo no menu: declarar em `core/navigation/nav.config.ts` com `modulo`/`roles`
- Estilo por token do `styles.css`; estados de tela com `<app-estado>`; excluir sempre via `ConfirmacaoService`
- Referência: `features/carga/` — mesma estrutura sempre

---

## 5. UX mínima (toda tela)

| Estado | Comportamento |
|--------|---------------|
| Loading | Spinner; desabilitar botão para evitar duplo submit |
| Empty | Mensagem + CTA ("Nenhum registro. Cadastrar primeiro?"). Tela vazia no primeiro acesso é o que mata trial — ver ARQUITETURA → Ativação |
| Error | Toast claro em português — sem stack trace |
| Success | Feedback após criar/editar/excluir |

- Formulários: Reactive Forms, validação exibida após `touched`
- Listagens: busca `q`, paginação, confirmação antes de excluir
- Dados sensíveis mascarados (LGPD)
- Textos em português claro

---

## 6. Segurança, perfis e tenant

- Permissão no **backend** (`@PreAuthorize`) — front só esconde UI
- Roles padrão: `PLATFORM_ADMIN`, `TENANT_ADMIN`, `USER` (ARQUITETURA → Autenticação e RBAC)
- Access token 15 min (só em memória no front) + refresh token rotacionado em **cookie httpOnly** — nunca localStorage, nunca logar token/senha
- Senha com BCrypt; rate limiting no login (5 falhas → bloqueio temporário)
- Fluxos obrigatórios de auth: esqueci-senha e convite de usuário por e-mail (tokens expiráveis — ARQUITETURA → Segurança do login)
- 404 (não 403) para recurso de outro tenant — não vazar existência
- Multi-tenant: isolamento por token; `empresa_id NOT NULL` em tabelas de negócio
- Isolamento automático via `@TenantId` desde o início (ARQUITETURA → Multi-tenant); query nativa não é filtrada — evitar ou filtrar manualmente
- Teste de isolamento entre 2 empresas no módulo de referência é obrigatório
- Módulos por assinatura (se aplicável): endpoint com `@RequiresModule(...)`; front esconde via `nav.config` + `moduleGuard` — mas quem bloqueia é o backend (ARQUITETURA → Planos e liberação de módulos)

---

## 7. Logs e monitoramento

- SLF4J com INFO / WARN / ERROR conforme tipo de falha — nunca `System.out.println`
- Nunca logar PII, senha ou token
- `/actuator/health` para deploy; `traceId` nos logs
- Alertas de produção chegam no **bot Telegram exclusivo do SaaS** (roteado pelo Alertmanager central — ARQUITETURA → Alertas); homolog não alerta
- 500 → mensagem genérica ao cliente, detalhe completo no log

---

## 8. Deploy e produção

- Docker multi-stage; secrets **somente** via env vars / `.env` (gitignored)
- Flyway roda na subida
- CORS restrito aos domínios do front
- Profiles: `dev` (seeds de demo) / `homolog` / `prod`
- Branches fixas: `dev` → deploy automático em **Homologação**; `main` → **Produção**. Nada entra na `main` sem validar em homolog
- VPS Hostinger padronizada: `setup-vps.sh` + `docker-compose.vps.yml` do kit (ARQUITETURA → Deploy) — os 2 ambientes na mesma VPS
- Monitoring: toda app expõe `/actuator/prometheus` (só para o IP do monitoring central) + agentes da stack `monitoring` na VPS
- CI: `.github/workflows/ci.yml` (molde do kit) — test → gitleaks/trivy → build/push com tag `sha-{commit}` → SSH deploy → smoke no `/actuator/health` → **rollback automático** se o health não subir
- Backup diário (`scripts/backup.sh`) com cópia off-site; **restore drill antes do go-live** — backup nunca restaurado não é backup

---

## 9. Definition of Done

- [ ] Dor real resolvida; MVP definido
- [ ] Fluxo seção 4 completo + teste de Service passando
- [ ] Loading, empty, error, success na tela
- [ ] Endpoint com `@PreAuthorize`; sem secrets hardcoded
- [ ] Migration versionada e **backward-compatible**; soft delete onde aplicável
- [ ] Multi-tenant: entity de negócio com `@TenantId`; `UNIQUE` composto com `empresa_id`; native queries (se houver) com filtro manual
- [ ] Listagem com índice `(empresa_id, filtro)` e sem N+1
- [ ] Módulo novo: código no enum `Modulo`, linha na tabela Módulos × planos, `@RequiresModule` no controller e seed em `plano_modulos`
- [ ] Recurso com limite de plano: quota checada no Service (409 + caminho de upgrade na tela)
- [ ] Validado em Homologação antes do merge na `main`
- [ ] `ARQUITETURA.md` (tabela de módulos) + skill seção 2 atualizados; `scripts/sync-skill.ps1` rodado

**Só na primeira entrega em produção (go-live):** backup rodando + **restore drill executado**, rollback testado (subir a tag `sha-` anterior), teste de isolamento entre 2 tenants passando, bot Telegram do SaaS criado.

---

## 10. Anti-padrões

- CRUD sem jornada de uso
- Permissão só no front
- Entity exposta na API
- Banco alterado sem migration / `ddl-auto: update` em prod
- Entity de negócio sem `@TenantId`, ou native query sem filtro de `empresa_id` — bug de segurança, não de funcionalidade
- `UNIQUE` global em tabela de negócio — quebra no segundo cliente
- `TenantContext` sem `clear()` no `finally` do filtro — a próxima requisição herda o tenant da anterior
- `semFiltroDeTenant(...)` fora de `platform/`, `billing/` ou `@Scheduled`
- Módulo pago sem checagem no backend — esconder só no menu não é bloqueio
- Refresh token (ou access token) em localStorage — usar cookie httpOnly + memória
- Estado de tela em campo comum em vez de `signal()` — na app zoneless a view não atualiza
- Vários `refresh()` em paralelo — o backend rotaciona o token e derruba a sessão por "reuso"
- Tela nova sem os quatro estados (carregando, vazio com CTA, erro, sucesso)
- Crase dentro de comentário no `template:` — fecha a template literal e quebra o build
- Webhook de billing sem idempotência ou sem validar assinatura
- Recurso com limite de plano criado sem checar quota (`plano_limites`)
- Listagem sem paginação, ou com relação carregada em loop (N+1)
- `DROP`/`RENAME` de coluna na mesma release que introduz o substituto — mata o rollback
- Deploy com tag móvel (`latest`) em vez de `sha-` — sem tag imutável não existe rollback
- Arquivo salvo como blob no banco (usar `FileStorageService`)
- try/catch no controller devolvendo erro manual (usar `GlobalExceptionHandler`)
- Hex de cor direto no componente (usar tokens do Design System)
- String de UI escrita direta no template em vez de chave de tradução
- Feature sem justificativa de produto

---

## 11. Evolução contínua

Ao fechar módulo:

1. `ARQUITETURA.md` — tabela de módulos
2. Esta skill — seção 2
3. `.\scripts\sync-skill.ps1` — propaga esta skill para `.cursor/` e `.claude/` (a fonte é `docs/skills/transportmanager/SKILL.md`; nunca edite as cópias)
4. Decisão estrutural tomada no caminho → ADR datado em `docs/adr/`

Invocar: `/transportmanager` (Cursor ou Claude Code)
