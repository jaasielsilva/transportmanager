-- Molde do kit — copiar para backend/src/main/resources/db/migration/V2__dunning_e_plataforma.sql
-- Suporte a: regua de dunning (cobranca de pagamento que falhou) e painel do dono do SaaS.
--
-- Expand-only: so ADD COLUMN nullable / com default e CREATE TABLE.
-- Nenhum DROP ou NOT NULL sobre coluna existente — a versao anterior da
-- aplicacao continua funcionando com este schema, entao o rollback continua valendo.

-- =====================================================================
-- Dunning
-- =====================================================================
ALTER TABLE empresas
    -- Etapa atual da regua: 0 = em dia. 1..4 = D+0, D+3, D+7, D+15.
    -- Persistida (e nao so calculada) porque e ela que garante que o job diario
    -- nao reenvie o mesmo e-mail toda madrugada.
    ADD COLUMN dunning_etapa INT NOT NULL DEFAULT 0,
    ADD COLUMN dunning_notificado_em TIMESTAMP NULL;

CREATE INDEX idx_empresas_dunning ON empresas (assinatura_status, dunning_etapa);
CREATE INDEX idx_empresas_trial ON empresas (assinatura_status, trial_expira_em);

-- Historico da regua. Alimenta o painel ("ha quanto tempo esta em atraso",
-- "quantas vezes ja avisamos") e serve de prova em contestacao de cobranca.
CREATE TABLE dunning_eventos (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id  BIGINT      NOT NULL,
    etapa       INT         NOT NULL,
    acao        VARCHAR(40) NOT NULL,   -- EMAIL_ENVIADO | SOMENTE_LEITURA | SUSPENSO | REGULARIZADO
    detalhes    VARCHAR(255) NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dunning_empresa FOREIGN KEY (empresa_id) REFERENCES empresas(id),
    -- SEM unique em (empresa_id, etapa, acao): a mesma empresa passa pela mesma
    -- etapa toda vez que atrasa de novo, e isso e legitimo. A tabela e um
    -- historico append-only.
    -- Quem impede aplicar a etapa duas vezes no MESMO ciclo e o
    -- empresas.dunning_etapa, que zera quando o cliente regulariza.
    INDEX idx_dunning_empresa (empresa_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================================
-- Ativacao (metrica do painel — "o cliente entendeu o valor?")
-- =====================================================================
ALTER TABLE empresas
    ADD COLUMN ativada_em TIMESTAMP NULL;

-- =====================================================================
-- ShedLock — impede que o job agendado rode em dobro com 2+ instancias.
-- Criada desde ja: quando a 2a instancia subir, o job ja esta protegido.
-- =====================================================================
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
