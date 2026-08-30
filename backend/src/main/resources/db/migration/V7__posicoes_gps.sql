-- Log de posicoes de GPS enviadas pelo motorista durante o transporte
-- (polling do navegador, ver features/rastreamento). Append-only: sem soft
-- delete e sem UNIQUE, porque nao e um dado editavel, e um evento.

CREATE TABLE posicoes_gps (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id    BIGINT NOT NULL,
    carga_id      BIGINT NOT NULL,
    motorista_id  BIGINT NOT NULL,
    latitude      DECIMAL(10,7) NOT NULL,
    longitude     DECIMAL(10,7) NOT NULL,
    registrado_em TIMESTAMP NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_posicoes_carga FOREIGN KEY (carga_id) REFERENCES cargas(id),
    CONSTRAINT fk_posicoes_motorista FOREIGN KEY (motorista_id) REFERENCES motoristas(id),
    INDEX idx_posicoes_carga_tempo (empresa_id, carga_id, registrado_em)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
