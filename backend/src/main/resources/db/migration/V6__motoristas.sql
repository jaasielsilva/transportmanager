-- Cadastro de motoristas. Primeira entidade real de "Fase 2 — atribuicao de
-- transporte" (ver skill secao 2): a carga ja tinha um motorista_id solto,
-- aqui ele passa a apontar para um registro de verdade.

CREATE TABLE motoristas (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id  BIGINT NOT NULL,
    usuario_id  BIGINT NULL,              -- vincula a um login (usuarios.id)
    nome        VARCHAR(150) NOT NULL,
    cnh         VARCHAR(20) NULL,
    telefone    VARCHAR(20) NULL,
    email       VARCHAR(150) NULL,
    ativo       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at  TIMESTAMP NULL,
    deleted_seq BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_motoristas_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    UNIQUE (empresa_id, email, deleted_seq),
    INDEX idx_motoristas_usuario (usuario_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE cargas
    ADD CONSTRAINT fk_cargas_motorista FOREIGN KEY (motorista_id) REFERENCES motoristas(id);
