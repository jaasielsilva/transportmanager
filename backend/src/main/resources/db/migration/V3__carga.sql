-- Molde do kit — backend/src/main/resources/db/migration/V3__carga.sql
-- Tabela do CRUD de referencia. E o modelo de TODA tabela de negocio do
-- projeto: copie a estrutura (colunas padrao, indices, chave unica) ao criar
-- a proxima.

CREATE TABLE cargas (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    -- Obrigatorio em tabela de negocio. E a coluna que o @TenantId usa para
    -- filtrar TODAS as queries JPA da entity sozinho.
    empresa_id  BIGINT       NOT NULL,
    nome        VARCHAR(150) NOT NULL,
    email       VARCHAR(150) NULL,
    telefone    VARCHAR(20)  NULL,
    documento   VARCHAR(20)  NULL,
    observacao  VARCHAR(500) NULL,
    ativo       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at  TIMESTAMP    NULL,
    -- Sentinela do soft delete: 0 enquanto vivo, o proprio id apos excluir.
    -- Sem ela, UNIQUE (empresa_id, documento) impediria recadastrar um
    -- documento que foi excluido — o registro apagado continuaria ocupando a
    -- chave para sempre.
    deleted_seq BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT fk_cargas_empresa FOREIGN KEY (empresa_id) REFERENCES empresas(id),
    -- UNIQUE SEMPRE composto com empresa_id. Global aqui significaria que a
    -- segunda empresa nao consegue cadastrar um documento que a primeira ja
    -- usou — o bug classico deste padrao, que so aparece com o 2o cliente.
    UNIQUE KEY uk_cargas_documento (empresa_id, documento, deleted_seq),
    -- Todo indice de listagem comeca por empresa_id: com o @TenantId, TODA
    -- query ja filtra por ele, e indice que nao comeca por empresa_id
    -- praticamente nao e usado.
    INDEX idx_cargas_empresa_nome (empresa_id, nome),
    INDEX idx_cargas_empresa_ativo (empresa_id, ativo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Quota do plano para este cadastro (checada no Service -> 409 com caminho de
-- upgrade). COMPLETO fica de fora de proposito: chave sem linha = ilimitado.
INSERT INTO plano_limites (plano_id, chave, valor)
SELECT id, 'MAX_CADASTROS', CASE codigo
        WHEN 'TRIAL'  THEN 20
        WHEN 'BASICO' THEN 200
        WHEN 'PRO'    THEN 2000
        END
  FROM planos
 WHERE codigo IN ('TRIAL', 'BASICO', 'PRO');
