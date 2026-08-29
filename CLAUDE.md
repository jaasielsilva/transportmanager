# TransportManager

Gerado pelo kit padrao-projeto v2.7.0.

Stack fixa: Java 21 + Spring Boot 3 (backend/), Angular 22 (frontend/), MySQL 8.
Multi-tenant obrigatorio: isolamento por empresa_id via @TenantId - o Hibernate
filtra as queries JPA automaticamente. Query nativa NAO e filtrada.

Setup, auth, tenant, ciclo comercial e o CRUD de referencia carga JA
EXISTEM e funcionam (back e front). Modulo novo se faz COPIANDO a estrutura de
features/carga - nao recriar o que ja esta pronto.

Comandos de desenvolvimento:
- docker compose up -d (banco na porta 3310 + mailpit em http://localhost:8025)
- cd backend && mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
- cd frontend && npm install && npm start (http://localhost:4200)

Antes de qualquer tarefa, leia:
- docs/ARQUITETURA.md — stack, pastas, contrato de API, RBAC, regras obrigatorias
- docs/skills/transportmanager/SKILL.md — como codar, UX minima, Definition of Done

Regras que nao se negociam:
- Nunca expor Entity na API (sempre DTO record) nem alterar schema fora do Flyway
- UNIQUE sempre composto com empresa_id
- Permissao no backend (@PreAuthorize); o front so esconde UI
- Secret so por variavel de ambiente
- Frontend zoneless: estado em signal()/computed(), senao a tela nao atualiza
- Token de acesso so em memoria; nunca em localStorage

Marcador 🔲 nos docs = ainda nao existe no codigo (nao inventar).
Ao fechar um modulo: atualizar ARQUITETURA.md, skill secao 2 e rodar
scripts/sync-skill.ps1 para sincronizar as copias da skill.