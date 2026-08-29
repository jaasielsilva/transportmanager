-- Molde do kit — copiar para backend/src/main/resources/db/migration/V1__init.sql
-- Baseline comum a todo SaaS do padrao: tenant, plano, auth, auditoria, billing.
-- O dominio do projeto entra a partir do V2.
--
-- Regras que se aplicam a TODA tabela criada daqui para frente:
--   1. empresa_id BIGINT NOT NULL em tabela de negocio (+ indice composto)
--   2. UNIQUE sempre composto com empresa_id — UNIQUE global quebra multi-tenant
--   3. created_at / updated_at sempre; deleted_at em dado de negocio
--   4. indice para toda coluna usada em filtro ou ordenacao

-- =====================================================================
-- Planos e modulos
-- =====================================================================
CREATE TABLE planos (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    codigo          VARCHAR(30)  NOT NULL UNIQUE,   -- TRIAL, BASICO, PRO, COMPLETO
    nome            VARCHAR(80)  NOT NULL,
    preco_mensal    DECIMAL(10,2) NOT NULL DEFAULT 0,
    ativo           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE plano_modulos (
    plano_id        BIGINT      NOT NULL,
    modulo          VARCHAR(40) NOT NULL,           -- codigo estavel do enum Modulo
    PRIMARY KEY (plano_id, modulo),
    CONSTRAINT fk_plano_modulos_plano FOREIGN KEY (plano_id) REFERENCES planos(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE plano_limites (
    plano_id        BIGINT      NOT NULL,
    chave           VARCHAR(40) NOT NULL,           -- MAX_USUARIOS, MAX_STORAGE_MB...
    valor           BIGINT      NOT NULL,
    PRIMARY KEY (plano_id, chave),
    CONSTRAINT fk_plano_limites_plano FOREIGN KEY (plano_id) REFERENCES planos(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================================
-- Tenant
-- =====================================================================
CREATE TABLE empresas (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    razao_social          VARCHAR(150) NOT NULL,
    documento             VARCHAR(20)  NOT NULL UNIQUE,   -- CNPJ: unico na plataforma
    plano_id              BIGINT       NOT NULL,
    assinatura_status     VARCHAR(20)  NOT NULL DEFAULT 'TRIALING',
                          -- TRIALING | ACTIVE | PAST_DUE | CANCELED
    trial_expira_em       DATE         NULL,
    gateway_customer_id   VARCHAR(80)  NULL,
    gateway_assinatura_id VARCHAR(80)  NULL,
    -- Marcos do ciclo de vida comercial: alimentam dunning, purge e o painel do dono.
    past_due_desde        TIMESTAMP    NULL,
    cancelada_em          TIMESTAMP    NULL,
    purge_em              DATE         NULL,             -- cancelada_em + retencao
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at            TIMESTAMP    NULL,
    CONSTRAINT fk_empresas_plano FOREIGN KEY (plano_id) REFERENCES planos(id),
    INDEX idx_empresas_status (assinatura_status),
    INDEX idx_empresas_purge (purge_em)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================================
-- Usuarios e acesso
-- =====================================================================
CREATE TABLE usuarios (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id        BIGINT       NOT NULL,
    nome              VARCHAR(120) NOT NULL,
    email             VARCHAR(150) NOT NULL,
    senha_hash        VARCHAR(100) NULL,               -- null ate aceitar o convite
    ativo             BOOLEAN      NOT NULL DEFAULT TRUE,
    ultimo_login_em   TIMESTAMP    NULL,
    tentativas_falhas SMALLINT     NOT NULL DEFAULT 0,
    bloqueado_ate     TIMESTAMP    NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at        TIMESTAMP    NULL,
    CONSTRAINT fk_usuarios_empresa FOREIGN KEY (empresa_id) REFERENCES empresas(id),
    -- UNIQUE GLOBAL, e nao (empresa_id, email) — a excecao consciente a regra
    -- do padrao. O login precisa achar o usuario ANTES de saber a empresa; com
    -- unique composta o mesmo e-mail existiria em duas empresas e a busca do
    -- login voltaria mais de um registro.
    -- Preco: uma pessoa pertence a uma empresa so. Ver o javadoc de Usuario.
    UNIQUE KEY uk_usuarios_email (email),
    INDEX idx_usuarios_empresa (empresa_id, ativo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usuario_roles (
    usuario_id  BIGINT      NOT NULL,
    role        VARCHAR(40) NOT NULL,   -- PLATFORM_ADMIN | TENANT_ADMIN | USER
    PRIMARY KEY (usuario_id, role),
    CONSTRAINT fk_usuario_roles_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Refresh token opaco, persistido para ser revogavel e rotacionado a cada uso.
CREATE TABLE refresh_tokens (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id   BIGINT      NOT NULL,
    token_hash   VARCHAR(64) NOT NULL UNIQUE,   -- SHA-256: nunca o token em claro
    expira_em    TIMESTAMP   NOT NULL,
    revogado_em  TIMESTAMP   NULL,
    substituido_por BIGINT   NULL,              -- cadeia de rotacao: detecta reuso
    ip           VARCHAR(45) NULL,
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    INDEX idx_refresh_usuario (usuario_id, revogado_em)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Convite de usuario e reset de senha na mesma tabela: mesmo ciclo (token de uso
-- unico, expiravel). Guardamos o hash — vazamento do banco nao vira sequestro de conta.
CREATE TABLE tokens_acesso (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id  BIGINT      NOT NULL,
    tipo        VARCHAR(20) NOT NULL,     -- CONVITE | RESET_SENHA
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expira_em   TIMESTAMP   NOT NULL,
    usado_em    TIMESTAMP   NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tokens_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    INDEX idx_tokens_usuario_tipo (usuario_id, tipo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================================
-- Auditoria e billing
-- =====================================================================
CREATE TABLE audit_logs (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id  BIGINT       NULL,        -- null em acao de plataforma
    usuario_id  BIGINT       NULL,
    acao        VARCHAR(60)  NOT NULL,    -- LOGIN, EXCLUSAO, MUDANCA_PERMISSAO, IMPERSONACAO...
    entidade    VARCHAR(60)  NULL,
    entidade_id BIGINT       NULL,
    detalhes    JSON         NULL,        -- nunca PII, senha ou token
    ip          VARCHAR(45)  NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_empresa_data (empresa_id, created_at),
    INDEX idx_audit_acao (acao, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Idempotencia do webhook: gateway reentrega o mesmo evento varias vezes.
-- Sem esta tabela, uma reentrega vira cobranca ou liberacao duplicada.
CREATE TABLE webhook_eventos (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    evento_id     VARCHAR(120) NOT NULL UNIQUE,   -- id do evento no gateway
    tipo          VARCHAR(60)  NOT NULL,
    payload       LONGTEXT     NOT NULL,
    processado_em TIMESTAMP    NULL,
    erro          LONGTEXT     NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_webhook_processado (processado_em)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================================
-- Seed minimo de planos (ajustar por projeto)
-- =====================================================================
INSERT INTO planos (codigo, nome, preco_mensal) VALUES
    ('TRIAL',    'Teste gratuito', 0.00),
    ('BASICO',   'Basico',        0.00),
    ('PRO',      'Profissional',  0.00),
    ('COMPLETO', 'Completo',      0.00);

-- Quais modulos cada plano habilita. Os codigos vem do enum Modulo — trocar um
-- nome aqui sem trocar no enum quebra o @RequiresModule em silencio.
--
-- TRIAL comeca com TUDO de proposito: quem esta avaliando precisa ver o produto
-- inteiro. Restringir o teste e a forma mais eficiente de nao vender.
INSERT INTO plano_modulos (plano_id, modulo)
SELECT id, m.modulo FROM planos p
  JOIN (SELECT 'CADASTROS' AS modulo UNION ALL
        SELECT 'OPERACAO'  UNION ALL
        SELECT 'RELATORIOS') m
 WHERE p.codigo IN ('TRIAL', 'COMPLETO');

INSERT INTO plano_modulos (plano_id, modulo)
SELECT id, 'CADASTROS' FROM planos WHERE codigo = 'BASICO';

INSERT INTO plano_modulos (plano_id, modulo)
SELECT id, m.modulo FROM planos p
  JOIN (SELECT 'CADASTROS' AS modulo UNION ALL SELECT 'OPERACAO') m
 WHERE p.codigo = 'PRO';

-- Quotas por plano. Checar no Service ao criar o recurso; excedeu -> 409 com
-- caminho de upgrade (LimiteDoPlanoException).
INSERT INTO plano_limites (plano_id, chave, valor)
SELECT id, 'MAX_USUARIOS', CASE codigo
        WHEN 'TRIAL'    THEN 3
        WHEN 'BASICO'   THEN 5
        WHEN 'PRO'      THEN 20
        ELSE 999 END
  FROM planos;
