## O que muda

<!-- Qual dor real isso resolve? -->

## Definition of Done (skill secao 9)

- [ ] Dor real resolvida; MVP definido
- [ ] Fluxo da secao 4 completo + teste de Service passando
- [ ] Loading, empty, error e success na tela
- [ ] Endpoint com @PreAuthorize; sem secret hardcoded
- [ ] Migration versionada e backward-compatible (expand/contract)
- [ ] Entity de negocio com @TenantId; UNIQUE composto com empresa_id
- [ ] Native query (se houver) com filtro manual de empresa_id
- [ ] Indice para as colunas de filtro/ordenacao da listagem; sem N+1
- [ ] Quota de plano checada no Service (409 com caminho de upgrade)
- [ ] Validado em Homologacao antes do merge na main
- [ ] docs/ARQUITETURA.md + skill secao 2 atualizados